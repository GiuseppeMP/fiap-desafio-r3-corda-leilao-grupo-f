package leilao;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import lombok.Getter;
import lombok.val;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.*;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Getter
// `TokenIssueFlowInitiator` means that we can start the flow directly (instead of
// solely in response to another flow).
@InitiatingFlow
// `StartableByRPC` means that a node operator can start the flow via RPC.
@StartableByRPC
// Like all states, implements `FlowLogic`.
public class RegistrarLeilaoFlowInitiator extends FlowLogic<Void> {

    private final String nomeDoLeilao;
    private final Date dataInicio;
    private final Date dataFim;

    public RegistrarLeilaoFlowInitiator(String nomeDoLeilao, Date dataInicio, Date dataFim) {
        this.nomeDoLeilao = nomeDoLeilao;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
    }

    private final ProgressTracker.Step VERIFICANDO_LEILAO = new ProgressTracker.Step("Verificando se já existe um leilão com esse nome no Vault. \uD83D\uDC4B");
    private final ProgressTracker.Step CRIANDO_LEILAO = new ProgressTracker.Step("Criando state do novo leilão. \uD83D\uDCAA");
    private final ProgressTracker.Step CONFIGURANDO_NOTARIO = new ProgressTracker.Step("Encontrando um notary na Rede. \uD83E\uDD16");
    private final ProgressTracker.Step CONFIGURANDO_TRANSACAO = new ProgressTracker.Step("Configurando a transação do novo leilão. \uD83E\uDD1D");
    private final ProgressTracker.Step FINALIZADO = new ProgressTracker.Step("Leilão criado e registrado com sucesso. \uD83D\uDE01");

    private final ProgressTracker progressTracker = new ProgressTracker(
            VERIFICANDO_LEILAO,
            CRIANDO_LEILAO,
            CONFIGURANDO_NOTARIO,
            CONFIGURANDO_TRANSACAO,   
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

        progressTracker.setCurrentStep(VERIFICANDO_LEILAO);

        // Extraimos todos os leiloes do vault
        List<StateAndRef<LeilaoState>> todosOsLeiloes = getServiceHub().getVaultService().queryBy(LeilaoState.class).getStates();

        // Procuramos se já existe um leilão com esse nome.
        if(todosOsLeiloes.stream().anyMatch(leilaoStateAndRef -> {
            LeilaoState leilaoState = leilaoStateAndRef.getState().getData();
            return leilaoState.getNomeDoLeilao().equals(this.nomeDoLeilao);
        })){
            throw new IllegalArgumentException("Leilão com esse nome já registrado.");
        }

        progressTracker.setCurrentStep(CRIANDO_LEILAO);

        LeilaoState novoLeilao = new LeilaoState(this.nomeDoLeilao, getOurIdentity(), new Date(), new Date());

        progressTracker.setCurrentStep(CONFIGURANDO_NOTARIO);

        // TODO Investigar melhor maneira de se obter um notário na rede
        val x500Name = CordaX500Name.parse(NOTARIO_FIAP_CITY());

        // We use the notary used by the input state.
        Party notaryDoLeilao = getServiceHub().getNetworkMapCache().getNotary(x500Name);


        progressTracker.setCurrentStep(CONFIGURANDO_TRANSACAO);

        // We build a transaction using a `TransactionBuilder`.
        TransactionBuilder txBuilder = new TransactionBuilder();

        // After creating the `TransactionBuilder`, we must specify which
        // notary it will use.
        txBuilder.setNotary(notaryDoLeilao);


        txBuilder.addOutputState(novoLeilao, LeilaoContract.ID);

        // We add the CadastrarItem command to the transaction.
        LeilaoContract.Commands.RegistrarLeilao commandData = new LeilaoContract.Commands.RegistrarLeilao();

        // Note that we also specific who is required to sign the transaction.
        List<PublicKey> requiredSigners = ImmutableList.of(
                novoLeilao.getLeiloeiro().getOwningKey());

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
        FlowSession ownerSession = initiateFlow(novoLeilao.getLeiloeiro());
        SignedTransaction fullySignedTx = subFlow(
                new CollectSignaturesFlow(partlySignedTx, ImmutableSet.of(ownerSession)));

        // We use `FinalityFlow` to automatically notarise the transaction
        // and have it recorded by all the `participants` of all the
        // transaction's states.
        subFlow(new FinalityFlow(fullySignedTx, Collections.emptyList()));


        progressTracker.setCurrentStep(FINALIZADO);

        return null;
    }

    @NotNull
    private static String NOTARIO_FIAP_CITY() {
        return "O=Notary,L=FIAP City,C=BR";
    }
}
