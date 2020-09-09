package leilao.domainmodel;


import lombok.*;
import lombok.extern.java.Log;
import net.corda.core.serialization.ConstructorForDeserialization;
import net.corda.core.serialization.CordaSerializable;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@EqualsAndHashCode
@ToString
@Log
@CordaSerializable
public class ItemDeLeilao implements Serializable {

    private @NonNull String codigo;
    @EqualsAndHashCode.Exclude
    private @NonNull String nome;
    @EqualsAndHashCode.Exclude
    private @NonNull Double valorInicial;
    @EqualsAndHashCode.Exclude
    private @NonNull String dono;

    public ItemDeLeilao(@NonNull String nome, @NonNull Double valorInicial, @NonNull String dono, String codigo) {
        this.codigo = codigo;
        this.nome = nome;
        this.valorInicial = valorInicial;
        this.dono = dono;
    }

    @ConstructorForDeserialization
    public ItemDeLeilao(@NonNull String nome, @NonNull Double valorInicial, @NonNull String dono) {
        this.codigo = UUID.randomUUID().toString();
        this.nome = nome;
        this.valorInicial = valorInicial;
        this.dono = dono;
    }
}
