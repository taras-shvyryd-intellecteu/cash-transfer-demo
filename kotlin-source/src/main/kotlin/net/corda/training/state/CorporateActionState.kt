package net.corda.training.state

import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import java.util.*

class CorporateActionState(val owner: Party,
                           val currency: Currency,
                           val investments: Map<Party, Amount<Currency>>,
                           val profit: Double,
                           override val linearId: UniqueIdentifier): LinearState {
    override val participants: List<Party>
        get() = investments.keys.toList().plusElement(owner)

    fun dividend(party: Party) = investments[party]!!.plus(Amount((investments[party]!!.quantity * profit).toLong(), currency))
}