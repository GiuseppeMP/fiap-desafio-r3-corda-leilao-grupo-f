package leilao;


import lombok.extern.java.Log;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;

import java.security.PublicKey;
import java.util.List;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;

@Log(topic="LeilaoContract")
public class LeilaoContract implements Contract {


    // Used to reference the contract in transactions.
    public static final String ID = LeilaoContract.class.getName();

    // Like all contracts, implements `Contract`.
    public interface Commands extends CommandData {
        class RegistrarLeilao implements LeilaoContract.Commands { }
        class Iniciar implements LeilaoContract.Commands { }
        class FazerLance implements LeilaoContract.Commands { }
        class ArrematarItem implements LeilaoContract.Commands { }
        class Encerrar implements LeilaoContract.Commands { }
        class CancelarItem implements LeilaoContract.Commands { }
        class CadastrarItem implements LeilaoContract.Commands { }
    }


    @Override
    public void verify(LedgerTransaction tx) throws IllegalArgumentException {
        CommandWithParties<LeilaoContract.Commands> command = requireSingleCommand(tx.getCommands(), LeilaoContract.Commands.class);
        if (command.getValue() instanceof LeilaoContract.Commands.CadastrarItem) {

            // Grabbing the transaction's contents.
            final LeilaoState leilaoStateInput = tx.inputsOfType(LeilaoState.class).get(0);
            final LeilaoState leilaoStateOutput = tx.outputsOfType(LeilaoState.class).get(0);

            log.info(leilaoStateOutput.toString());

            if(leilaoStateOutput.getDataFim().after(leilaoStateOutput.getDataInicio())){
                throw new IllegalArgumentException("Data final do leilão não pode ser menor ou igual que data inicial.");
            }

            // Checking the transaction's required signers.
            final List<PublicKey> requiredSigners = command.getSigners();
            if (!(requiredSigners.contains(leilaoStateInput.getLeiloeiro().getOwningKey())))
                throw new IllegalArgumentException("Para cadastrar um item no Leilão, é preciso da assinatura do Leiloeiro.");

        }
    }


}
