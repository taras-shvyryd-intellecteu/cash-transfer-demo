package net.corda.training.contract

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

@LegalProseReference(uri = "<prose_contract_uri>")
class CorporateActionContract: Contract {
    companion object {
        @JvmStatic
        val IOU_CONTRACT_ID = "net.corda.training.contract.CorporateActionContract"
    }

    interface Commands: CommandData{
        class Offer: TypeOnlyCommandData(), Commands
        class Approve: TypeOnlyCommandData(), Commands
        class Open: TypeOnlyCommandData(), Commands
        class Close: TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<CorporateActionContract.Commands>()
        when (command.value){
            is Commands.Offer -> {

            }
            is Commands.Approve -> {

            }
            is Commands.Open -> {

            }
            is Commands.Close -> {

            }
        }
    }
}