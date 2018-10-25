package net.corda.training.state

import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import java.util.*

data class CorporateActionState(val owner: Party,
                                val currency: Currency,
                                val profit: Double,
                                var investments: Map<Party, Amount<Currency>> = mapOf(),
                                override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState {
    override val participants: List<Party>
        get() = investments.keys.toList().plusElement(owner)

    fun invest(party: Party, amount: Amount<Currency>): CorporateActionState {
        val invested = investments[party]
        return if (invested != null) {
            copy(investments = investments.plus(party to invested.plus(amount)))
        } else{
            copy(investments = investments.plus(party to amount))
        }
    }
}