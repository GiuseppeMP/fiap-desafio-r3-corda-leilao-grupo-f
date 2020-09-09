package leilao;

import leilao.domainmodel.ItemDeLeilao;
import lombok.*;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.serialization.ConstructorForDeserialization;
import net.corda.core.serialization.CordaSerializable;

import java.io.Serializable;
import java.util.*;


// Like all states, implements `ContractState`.
@BelongsToContract(LeilaoContract.class)
@Getter
@Setter
@CordaSerializable
@ToString
public class LeilaoState implements ContractState, Serializable {

    // The attributes that will be stored on the ledger as part of the state.
    @NonNull private final String nomeDoLeilao;
    @NonNull private final Party leiloeiro;
    private final Set<ItemDeLeilao> itensDoLeilao;
    private final Set<Party> participantes;
    private final Date dataInicio;
    private final Date dataFim;
    private final String situacao;
    

    public LeilaoState(@NonNull String nomeDoLeilao, @NonNull Party leiloeiro, Date dataInicio, Date dataFim) {
        this.nomeDoLeilao = nomeDoLeilao;
        this.leiloeiro = leiloeiro;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.participantes= new HashSet<>();
        this.participantes.add(leiloeiro);
        this.situacao = "Novo";
        this.itensDoLeilao = new HashSet<>();
    }

    @ConstructorForDeserialization
    public LeilaoState(@NonNull String nomeDoLeilao, @NonNull Party leiloeiro, Set<Party> participantes, Date dataInicio, Date dataFim, String situacao, Set<ItemDeLeilao> itensDoLeilao) {
        this.nomeDoLeilao = nomeDoLeilao;
        this.leiloeiro = leiloeiro;
        this.participantes = participantes;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.situacao = situacao;
        this.itensDoLeilao = itensDoLeilao;
    }

    @Override
    public List<AbstractParty> getParticipants() {
        return new ArrayList<>(this.participantes);
    }
}
