package com.codewithfk.model

import java.math.BigDecimal
import java.time.LocalDate

data class OwnerDailyStats(
    val date: LocalDate,
    val totalRevenue: BigDecimal,
    val totalOrders: Int,
    val pendingOrders: Int
)
