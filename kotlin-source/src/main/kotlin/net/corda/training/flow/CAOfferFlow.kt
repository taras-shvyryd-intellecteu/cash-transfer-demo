package net.corda.training.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.training.contract.CorporateActionContract
import net.corda.training.state.CorporateActionState

/**
 * @author Taras Shvyryd
 */
@InitiatingFlow
@StartableByRPC
class CAOfferFlow(val state: CorporateActionState) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        val offerCommand = Command(CorporateActionContract.Commands.Offer(), state.participants.map { it.owningKey })

        val builder = TransactionBuilder(notary = notary)

        builder.addOutputState(state, CorporateActionContract.CA_CONTRACT_ID)
        builder.addCommand(offerCommand)

        builder.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(builder)

        val sessions = (state.participants - ourIdentity).map { initiateFlow(it) }.toSet()
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        return subFlow(FinalityFlow(stx))
    }
}

@InitiatedBy(CAOfferFlow::class)
class CAOfferFlowResponder(val flowSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be an corporate action transaction" using (output is CorporateActionState)
            }
        }
        subFlow(signedTransactionFlow)
    }

}