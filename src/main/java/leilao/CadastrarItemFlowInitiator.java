package leilao;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import examples.ArtContract;
import leilao.domainmodel.ItemDeLeilao;
import lombok.Getter;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.serialization.ConstructorForDeserialization;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.security.PublicKey;
import java.util.Collections;
import java.util.List;

@Getter
// `TokenIssueFlowInitiator` means that we can start the flow directly (instead of
// solely in response to another flow).
@InitiatingFlow
// `StartableByRPC` means that a node operator can start the flow via RPC.
@StartableByRPC
// Like all states, implements `FlowLogic`.
public class CadastrarItemFlowInitiator extends FlowLogic<Void> {

    private final Party vendedor;
    private final ItemDeLeilao itemDeLeilaoParaCadastrar;
    private final String nomeDoLeilaoAlvo;

    @ConstructorForDeserialization
    public CadastrarItemFlowInitiator(Party vendedor, String nomeDoLeilaoAlvo, String nomeItem, Double valorInicial, String dono) {
        this.itemDeLeilaoParaCadastrar = new ItemDeLeilao(nomeItem, valorInicial, dono);
        this.vendedor = vendedor;
        this.nomeDoLeilaoAlvo = nomeDoLeilaoAlvo;
    }
    private final ProgressTracker.Step BEM_VINDO_VENDENDOR = new ProgressTracker.Step("Bem-vindo vendedor, iniciando fluxo de registro do item. \uD83D\uDC4B");
    private final ProgressTracker.Step PROCURANDO_LEILAO = new ProgressTracker.Step("Procurando o Leilão que você quer registrar a venda. \uD83D\uDCAA");
    private final ProgressTracker.Step CONFIGURANDO_NOTARIO = new ProgressTracker.Step("Encontrando um notary na Rede. \uD83E\uDD16");
    private final ProgressTracker.Step CONFIGURANDO_TRANSACAO = new ProgressTracker.Step("Configurando a transação do novo item no Leilão informado. \uD83E\uDD1D");
    private final ProgressTracker.Step REGISTRANDO_ITEM_AO_INVENTARIO = new ProgressTracker.Step("Adicionando item ao inventário do Leilão. \uD83D\uDC8E");
    private final ProgressTracker.Step FINALIZADO = new ProgressTracker.Step("Item cadastrado no leilão com sucesso. \uD83D\uDE01");


    private final ProgressTracker progressTracker = new ProgressTracker(
            BEM_VINDO_VENDENDOR,
            PROCURANDO_LEILAO,
            CONFIGURANDO_NOTARIO,
            CONFIGURANDO_TRANSACAO,
            REGISTRANDO_ITEM_AO_INVENTARIO,
            FINALIZADO
    );

    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    // Must be marked `@Suspendable` to allow the flow to be suspended
    // mid-execution.
    @Suspendable
    @Override
    public Void call() throws FlowException {


        progressTracker.setCurrentStep(BEM_VINDO_VENDENDOR);
        // Extraimos todos os leiloes do vault
        List<StateAndRef<LeilaoState>> todosOsLeiloes = getServiceHub().getVaultService().queryBy(LeilaoState.class).getStates();

        progressTracker.setCurrentStep(PROCURANDO_LEILAO);
        // Procuramos pelo leilão que está se querendo adicionar o item
        StateAndRef<LeilaoState> leilaoAtualComRef = todosOsLeiloes.stream().filter(leilaoStateAndRef -> {
            LeilaoState leilaoState = leilaoStateAndRef.getState().getData();
            return leilaoState.getNomeDoLeilao().equals(nomeDoLeilaoAlvo);
        }).findAny().orElseThrow(() -> new IllegalArgumentException("Leilão não encontrado com o nome enviado."));


        progressTracker.setCurrentStep(CONFIGURANDO_NOTARIO);
        // We use the notary used by the input state.
        Party notaryDoLeilao = leilaoAtualComRef.getState().getNotary();


        progressTracker.setCurrentStep(CONFIGURANDO_TRANSACAO);
        // We build a transaction using a `TransactionBuilder`.
        TransactionBuilder txBuilder = new TransactionBuilder();

        // After creating the `TransactionBuilder`, we must specify which
        // notary it will use.
        txBuilder.setNotary(notaryDoLeilao);

        // We add the input ArtState to the transaction.
        txBuilder.addInputState(leilaoAtualComRef);

        progressTracker.setCurrentStep(REGISTRANDO_ITEM_AO_INVENTARIO);

        final LeilaoState leilaoAtualState = leilaoAtualComRef.getState().getData();


        LeilaoState leilaoStateNovo = new LeilaoState(
                leilaoAtualState.getNomeDoLeilao(),
                leilaoAtualState.getLeiloeiro(),
                leilaoAtualState.getDataInicio(),
                leilaoAtualState.getDataFim()
        );

        leilaoStateNovo.getItensDoLeilao().addAll(leilaoAtualState.getItensDoLeilao());

        leilaoStateNovo.getItensDoLeilao().add(this.itemDeLeilaoParaCadastrar);

        leilaoStateNovo.getParticipantes().add(this.vendedor);

        txBuilder.addOutputState(leilaoStateNovo, LeilaoContract.ID);

        // We add the CadastrarItem command to the transaction.
        LeilaoContract.Commands.CadastrarItem commandData = new LeilaoContract.Commands.CadastrarItem();

        // Note that we also specific who is required to sign the transaction.
        List<PublicKey> requiredSigners = ImmutableList.of(
                leilaoAtualState.getLeiloeiro().getOwningKey());

        txBuilder.addCommand(commandData, requiredSigners);

        // We check that the transaction builder we've created meets the
        // contracts of the input and output states.
        txBuilder.verify(getServiceHub());

        // We finalise the transaction builder by signing it,
        // converting it into a `SignedTransaction`.
        SignedTransaction partlySignedTx = getServiceHub().signInitialTransaction(txBuilder);


        // We use `CollectSignaturesFlow` to automatically gather a
        // signature from each counterparty. The counterparty will need to
        // call `SignTransactionFlow` to decided whether or not to sign.
        FlowSession ownerSession = initiateFlow(this.vendedor);
        SignedTransaction fullySignedTx = subFlow(
                new CollectSignaturesFlow(partlySignedTx, ImmutableSet.of(ownerSession)));

        // We use `FinalityFlow` to automatically notarise the transaction
        // and have it recorded by all the `participants` of all the
        // transaction's states.
        subFlow(new FinalityFlow(fullySignedTx, Collections.emptyList()));

        progressTracker.setCurrentStep(FINALIZADO);

        return null;
    }
}
