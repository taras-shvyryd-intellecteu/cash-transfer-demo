package net.corda.training.contract

import net.corda.core.contracts.*
import net.corda.core.contracts.Requirements.using
import net.corda.core.transactions.LedgerTransaction
import net.corda.training.state.CorporateActionState

@LegalProseReference(uri = "<prose_contract_uri>")
class CorporateActionContract : Contract {
    companion object {
        @JvmStatic
        val CA_CONTRACT_ID = "net.corda.training.contract.CorporateActionContract"
    }

    interface Commands : CommandData {
        class Offer : TypeOnlyCommandData(), Commands
        class Invest : TypeOnlyCommandData(), Commands
        class Open : TypeOnlyCommandData(), Commands
        class Close : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<CorporateActionContract.Commands>()
        when (command.value) {
            is Commands.Offer -> requireThat {
                "No inputs should be consumed when offering corporate action" using (tx.inputs.isEmpty())
                "Only one output state should be created when offering corporate action." using (tx.outputs.size == 1)
                val corporateAction = tx.outputStates.single() as CorporateActionState
                "Offered corporate action must be investable" using (corporateAction.investable)
                "Offered corporate action must have a positive profit coefficient" using (corporateAction.profit > 0)
                "The owner and investor cannot have the same identity" using (corporateAction.owner != corporateAction.investor)
                "Only owner and investor may sign corporate action offer transaction" using (command.signers.toSet() == corporateAction.participants.map { it.owningKey }.toSet())
            }
            is Commands.Invest -> {
                "An corporate action invest transaction should only consume one input state." using (tx.inputs.size == 1)
                "An corporate action invest transaction should only create one output state." using (tx.outputs.size == 1)
                val input = tx.inputStates.single() as CorporateActionState
                val output = tx.outputStates.single() as CorporateActionState
                "Both input and output states must be investable" using (input.investable && output.investable)
                "Only the investment property may change" using (output == input.copy(investment = output.investment))
                "Investment property must change on invest command" using (output.investment != input.investment)
                "Only owner and investor may sign corporate action invest transaction" using (command.signers.toSet() ==
                        (input.participants.map { it.owningKey }.toSet() `union` output.participants.map { it.owningKey }.toSet()))
            }
            is Commands.Open -> {
                "An corporate action open transaction should only consume one input state." using (tx.inputs.size == 1)
                "An corporate action open transaction should only create one output state." using (tx.outputs.size == 1)
                val input = tx.inputStates.single() as CorporateActionState
                val output = tx.outputStates.single() as CorporateActionState
                "Input state must be investable" using (input.investable)
                "Output state must not be investable" using (!output.investable)
                "Owner must sign corporate action open transaction" using (command.signers.toSet().contains(input.owner.owningKey))
            }
            is Commands.Close -> {
                "An corporate action close transaction should only consume one input state." using (tx.inputs.size == 1)
                "An corporate action close transaction should only create one output state." using (tx.outputs.size == 1)
                val input = tx.inputStates.single() as CorporateActionState
                val output = tx.outputStates.single() as CorporateActionState
                "Both input and output states must not be investable" using (!input.investable && !output.investable)
                "Only the profit property may change" using (output == input.copy(profit = output.profit))
                "Owner must sign corporate action close transaction" using (command.signers.toSet().contains(input.owner.owningKey))
            }
        }
    }
}