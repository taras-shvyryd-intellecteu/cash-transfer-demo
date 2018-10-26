package net.corda.training.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.training.contract.CorporateActionContract
import net.corda.training.state.CorporateActionState

/**
 * @author Taras Shvyryd
 */
@InitiatingFlow
@StartableByRPC
class CAOpenFlow(val linearId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
        val caToOpen = serviceHub.vaultService.queryBy<CorporateActionState>(queryCriteria).states.single()
        val counterparty = caToOpen.state.data.investor

        if (ourIdentity != caToOpen.state.data.owner) {
            throw IllegalArgumentException("Corporate action open flow must be initiated by the owner.")
        }

        val notary = caToOpen.state.notary
        val builder = TransactionBuilder(notary = notary)
        val investCommand = Command(CorporateActionContract.Commands.Open(), listOf(counterparty.owningKey, ourIdentity.owningKey))
        builder.addCommand(investCommand)
        builder.addInputState(caToOpen)
        val outputCA = caToOpen.state.data.copy(investable = false)
        builder.addOutputState(outputCA, CorporateActionContract.CA_CONTRACT_ID)

        builder.verify(serviceHub)
        val myKeysToSign = listOf(ourIdentity.owningKey, counterparty.owningKey)
        val ptx = serviceHub.signInitialTransaction(builder, myKeysToSign)

        val counterpartySession = initiateFlow(counterparty)

        subFlow(IdentitySyncFlow.Send(counterpartySession, ptx.tx))

        val stx = subFlow(CollectSignaturesFlow(ptx, listOf(counterpartySession), myOptionalKeys = myKeysToSign))

        return subFlow(FinalityFlow(stx))
    }
}

@InitiatedBy(CAOpenFlow::class)
class CAOpenFlowResponder(val flowSession: FlowSession) : FlowLogic<Unit>() {
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