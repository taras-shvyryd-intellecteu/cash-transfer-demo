package net.corda.training.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.Requirements.using
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.getCashBalance
import net.corda.training.contract.CorporateActionContract
import net.corda.training.state.CorporateActionState
import java.util.*

/**
 * @author Taras Shvyryd
 */
@InitiatingFlow
@StartableByRPC
class CAInvestFlow(val linearId: UniqueIdentifier, val amount: Amount<Currency>) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
        val caToInvest = serviceHub.vaultService.queryBy<CorporateActionState>(queryCriteria).states.single()
        val counterparty = caToInvest.state.data.owner

        if (ourIdentity != caToInvest.state.data.investor) {
            throw IllegalArgumentException("Corporate action investment flow must be initiated by the investor.")
        }

        val notary = caToInvest.state.notary
        val builder = TransactionBuilder(notary = notary)

        val cashBalance = serviceHub.getCashBalance(amount.token)
        if (cashBalance < amount) {
            throw IllegalArgumentException("Investor has only $cashBalance but needs $amount to invest.")
        }

        val (_, cashKeys) = Cash.generateSpend(serviceHub, builder, amount, ourIdentityAndCert, counterparty)

        val investCommand = Command(CorporateActionContract.Commands.Invest(), listOf(counterparty.owningKey, ourIdentity.owningKey))
        builder.addCommand(investCommand)
        builder.addInputState(caToInvest)

        val investedCA: CorporateActionState = caToInvest.state.data.invest(amount)
        builder.addOutputState(investedCA, CorporateActionContract.CA_CONTRACT_ID)

        builder.verify(serviceHub)
        val myKeysToSign = (cashKeys.toSet() + ourIdentity.owningKey).toList()
        val ptx = serviceHub.signInitialTransaction(builder, myKeysToSign)

        val counterpartySession = initiateFlow(counterparty)

        subFlow(IdentitySyncFlow.Send(counterpartySession, ptx.tx))

        val stx = subFlow(CollectSignaturesFlow(ptx, listOf(counterpartySession), myOptionalKeys = myKeysToSign))

        return subFlow(FinalityFlow(stx))
    }
}

class CAInvestFlowResponder(val flowSession: FlowSession) : FlowLogic<Unit>() {
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