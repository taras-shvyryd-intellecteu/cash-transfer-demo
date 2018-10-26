package net.corda.training.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.contracts.asset.Cash
import net.corda.training.contract.CorporateActionContract
import net.corda.training.state.CorporateActionState

/**
 * @author Taras Shvyryd
 */
@InitiatingFlow
@StartableByRPC
class CACloseFlow(val linearId: UniqueIdentifier): FlowLogic<SignedTransaction>(){
    @Suspendable
    override fun call(): SignedTransaction {
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
        val caToInvest = serviceHub.vaultService.queryBy<CorporateActionState>(queryCriteria).states.single()
        val counterparty = caToInvest.state.data.investor

        if (ourIdentity != caToInvest.state.data.owner) {
            throw IllegalArgumentException("Corporate action close flow must be initiated by the owner.")
        }

        val notary = caToInvest.state.notary
        val builder = TransactionBuilder(notary = notary)
        val investCommand = Command(CorporateActionContract.Commands.Open(), listOf(counterparty.owningKey, ourIdentity.owningKey))
        builder.addCommand(investCommand)
        builder.addInputState(caToInvest)

        val amount = Amount((caToInvest.state.data.investment.quantity * caToInvest.state.data.profit).toLong(), caToInvest.state.data.currency)
        val (_, cashKeys) = Cash.generateSpend(serviceHub, builder, amount, ourIdentityAndCert, counterparty)

        builder.verify(serviceHub)
        val myKeysToSign = (cashKeys.toSet() + ourIdentity.owningKey).toList()
        val ptx = serviceHub.signInitialTransaction(builder, myKeysToSign)

        val counterpartySession = initiateFlow(counterparty)

        subFlow(IdentitySyncFlow.Send(counterpartySession, ptx.tx))

        val stx = subFlow(CollectSignaturesFlow(ptx, listOf(counterpartySession), myOptionalKeys = myKeysToSign))

        return subFlow(FinalityFlow(stx))
    }
}

@InitiatedBy(CACloseFlow::class)
class CACloseFlowResponder(val flowSession: FlowSession): FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // Receiving information about anonymous identities
        subFlow(IdentitySyncFlow.Receive(flowSession))

        // signing transaction
        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) {
            }
        }

        subFlow(signedTransactionFlow)
    }

}