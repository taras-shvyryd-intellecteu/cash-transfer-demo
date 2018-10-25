package net.corda.training.state

import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import java.util.*

/**
 * @param profit used to get final action cost by multiplying on investment amount
 */
data class CorporateActionState(val owner: Party,
                                val currency: Currency,
                                val profit: Double,
                                val investor: Party,
                                val investable: Boolean = true,
                                val investment: Amount<Currency> = Amount(0, currency),
                                override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState {
    override val participants: List<Party>
        get() = listOf(owner, investor)

    fun invest(amount: Amount<Currency>): CorporateActionState {
        return copy(investment = investment.plus(amount))
    }
}