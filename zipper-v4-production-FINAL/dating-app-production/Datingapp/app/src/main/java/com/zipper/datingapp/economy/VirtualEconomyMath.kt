package com.zipper.datingapp.economy

import com.zipper.datingapp.data.Gift

/**
 * Integer-safe virtual economy helpers (no floating-point rounding drift).
 *
 * Call payouts (receiver of the minute charge), based on payer + receiver genders when the payer is
 * an unambiguous **male**: **male→male 30%**, **male→female 50%** of charged 💎 as beans.
 * For any other payer (female or ambiguous), legacy receiver-only rates apply: **female receiver 50%**,
 * **male receiver 40%**. Unknown/ambiguous receiver with male payer falls back to the same receiver-only
 * split when possible.
 * Gift payouts: **50%** of 💎 spend to receiver beans (all CRM / premium / standard); lucky gifts use a separate **5%** path in Cloud Functions only.
 */
object VirtualEconomyMath {

    /** Default audio rate (💎/min) when [com.zipper.datingapp.data.UserProfile.customAudioPrice] is null. */
    const val DEFAULT_CALL_AUDIO_DIAMONDS_PER_MIN = 1000

    /** Default video rate (💎/min) when [com.zipper.datingapp.data.UserProfile.customVideoPrice] is null. */
    const val DEFAULT_CALL_VIDEO_DIAMONDS_PER_MIN = 1500

    /**
     * Beans earned by the **call receiver** for this diamond charge (truncated integer %).
     */
    fun callReceiverBeansEarned(
        totalDiamondCost: Long,
        payerGenderRaw: String?,
        receiverGenderRaw: String?,
    ): Long {
        if (totalDiamondCost <= 0L) return 0L
        val payerMale = isMaleGender(payerGenderRaw) && !isFemaleGender(payerGenderRaw)
        val bps = if (payerMale) {
            when {
                isMaleGender(receiverGenderRaw) && !isFemaleGender(receiverGenderRaw) -> 30L
                isFemaleGender(receiverGenderRaw) && !isMaleGender(receiverGenderRaw) -> 50L
                else -> callReceiverBeansBpsReceiverOnly(receiverGenderRaw)
            }
        } else {
            callReceiverBeansBpsReceiverOnly(receiverGenderRaw)
        }
        if (bps <= 0L) return 0L
        return (totalDiamondCost * bps) / 100L
    }

    /** Legacy split when payer is not an unambiguous male, or as fallback for ambiguous receiver with male payer. */
    private fun callReceiverBeansBpsReceiverOnly(receiverGenderRaw: String?): Long =
        when {
            isFemaleGender(receiverGenderRaw) && !isMaleGender(receiverGenderRaw) -> 50L
            isMaleGender(receiverGenderRaw) && !isFemaleGender(receiverGenderRaw) -> 40L
            else -> 0L
        }

    /**
     * Beans earned by the gift receiver from the total diamond spend for this gift send (50% for all catalog gifts).
     * Lucky gifts are billed via `processLuckyGifts` and use a separate **5%** rate on the server.
     */
    fun giftReceiverBeansEarned(totalDiamondCost: Long, @Suppress("UNUSED_PARAMETER") gift: Gift): Long {
        if (totalDiamondCost <= 0L) return 0L
        return (totalDiamondCost * 50L) / 100L
    }

    fun isFemaleGender(genderRaw: String?): Boolean {
        val g = genderRaw?.trim()?.lowercase() ?: return false
        if (g in setOf("female", "woman", "girl", "girls", "f")) return true
        return g.startsWith("female")
    }

    fun isMaleGender(genderRaw: String?): Boolean {
        val g = genderRaw?.trim()?.lowercase() ?: return false
        if (g in setOf("male", "man", "m", "boy")) return true
        return g.startsWith("male")
    }

    /**
     * One-to-one audio/video calls when **both** parties have an unambiguous binary gender (male XOR female on each).
     * Allows male–female, male–male, and female–female. Blocks unknown / non-binary / conflicting flags.
     */
    fun isBinaryGenderCallPairAllowed(genderA: String?, genderB: String?): Boolean {
        val aMale = isMaleGender(genderA)
        val aFemale = isFemaleGender(genderA)
        val bMale = isMaleGender(genderB)
        val bFemale = isFemaleGender(genderB)
        val aOk = (aMale && !aFemale) || (!aMale && aFemale)
        val bOk = (bMale && !bFemale) || (!bMale && bFemale)
        return aOk && bOk
    }

    /** App-wide max level (profile / unlocks). */
    const val MAX_APP_LEVEL: Int = 11

    /** Female: level 4 from cumulative beans earned (gifts/calls). */
    private const val FEMALE_LEVEL_4_BEANS: Long = 500_000L

    /** Female: level 11 at 1B beans. */
    private const val FEMALE_LEVEL_11_BEANS: Long = 1_000_000_000L

    /** Male: level 4 from cumulative 💎 spent sending gifts. */
    private const val MALE_LEVEL_4_SPEND: Long = 1_000_000L

    /** Male: level 11 at 1B 💎 spent. */
    private const val MALE_LEVEL_11_SPEND: Long = 1_000_000_000L

    /**
     * Female level 1–11 from lifetime [beans] (earned). Level 4 at 500k; level 11 at 1B.
     */
    fun femaleLevelFromBeans(beans: Long): Int {
        val b = beans.coerceAtLeast(0L)
        if (b >= FEMALE_LEVEL_11_BEANS) return MAX_APP_LEVEL
        if (b >= FEMALE_LEVEL_4_BEANS) {
            val span = (FEMALE_LEVEL_11_BEANS - FEMALE_LEVEL_4_BEANS).toDouble()
            val prog = ((b - FEMALE_LEVEL_4_BEANS).toDouble() / span).coerceIn(0.0, 1.0)
            val extra = (prog * 7.0).toInt().coerceIn(0, 7)
            return (4 + extra).coerceIn(4, 10)
        }
        if (b <= 0L) return 1
        // Levels 1–3 below 500k beans (thirds of the range).
        return when {
            b < 166_667L -> 1
            b < 333_334L -> 2
            else -> 3
        }
    }

    /**
     * Male level 1–11 from lifetime gift send spend ([totalGiftDiamondsSpent] in 💎).
     * Level 4 at 1M spend; level 11 at 1B spend.
     */
    fun maleGiverLevelFromGiftSpend(totalGiftDiamondsSpent: Long): Int {
        val s = totalGiftDiamondsSpent.coerceAtLeast(0L)
        if (s >= MALE_LEVEL_11_SPEND) return MAX_APP_LEVEL
        if (s >= MALE_LEVEL_4_SPEND) {
            val span = (MALE_LEVEL_11_SPEND - MALE_LEVEL_4_SPEND).toDouble()
            val prog = ((s - MALE_LEVEL_4_SPEND).toDouble() / span).coerceIn(0.0, 1.0)
            val extra = (prog * 6.0).toInt().coerceIn(0, 6)
            return (4 + extra).coerceIn(4, 10)
        }
        if (s <= 0L) return 1
        return when {
            s < 333_334L -> 1
            s < 666_667L -> 2
            else -> 3
        }
    }
}

/** True for premium / 3D / CRM catalog rows (economy metadata; [giftReceiverBeansEarned] is 50% for all gifts). */
val Gift.isPremiumEconomyGift: Boolean
    get() {
        if (isCrmGift) return true
        return when (category.trim().uppercase()) {
            "3D_PREMIUM", "CRM_PREMIUM" -> true
            else -> false
        }
    }
