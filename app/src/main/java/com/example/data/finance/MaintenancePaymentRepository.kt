package com.example.data.finance

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Repositorio del Histórico de Pagos y Cuotas de Mantenimiento.
 * Gestiona el búfer offline en Room para consultas inmediatas sin conexión a internet.
 */
class MaintenancePaymentRepository(
    private val paymentDao: MaintenancePaymentDao
) {

    fun getPaymentHistoryFlow(unitId: String): Flow<List<MaintenancePaymentEntity>> {
        return paymentDao.getPaymentsForUnit(unitId)
    }

    suspend fun ensureInitialPaymentsSeeded(unitId: String, condominiumId: String) = withContext(Dispatchers.IO) {
        val count = paymentDao.getPaymentCountForUnit(unitId)
        if (count == 0) {
            val cal = Calendar.getInstance()
            val cleanUnit = unitId.replace(" ", "").uppercase()

            // Generar los últimos 3 recibos mensuales liquidados para este usuario
            val samplePayments = listOf(
                MaintenancePaymentEntity(
                    id = "PAY-202609-$cleanUnit",
                    unitId = unitId,
                    condominiumId = condominiumId,
                    receiptFolio = "REC-20260905-1420",
                    concept = "Cuota Ordinaria Mantenimiento - Septiembre 2026",
                    amount = 1450.00,
                    paymentDateMillis = System.currentTimeMillis() - (7 * 24 * 3600 * 1000L), // hace 7 días
                    paymentMethod = "SPEI Transferencia Bancaria",
                    status = "LIQUIDADO",
                    bankReference = "SPEI-BBVA-78901245",
                    digitalSignatureSha = "SHA256:7B8F9A02C1E34D99FA21",
                    breakdownCuota = 1200.0,
                    breakdownFondoReserva = 150.0,
                    breakdownAmenidades = 100.0
                ),
                MaintenancePaymentEntity(
                    id = "PAY-202608-$cleanUnit",
                    unitId = unitId,
                    condominiumId = condominiumId,
                    receiptFolio = "REC-20260804-9912",
                    concept = "Cuota Ordinaria Mantenimiento - Agosto 2026",
                    amount = 1450.00,
                    paymentDateMillis = System.currentTimeMillis() - (38 * 24 * 3600 * 1000L), // mes pasado
                    paymentMethod = "Tarjeta de Débito (En Línea)",
                    status = "LIQUIDADO",
                    bankReference = "PAY-STRIPE-4421908",
                    digitalSignatureSha = "SHA256:3E1B9C55D28A1102EE33",
                    breakdownCuota = 1200.0,
                    breakdownFondoReserva = 150.0,
                    breakdownAmenidades = 100.0
                ),
                MaintenancePaymentEntity(
                    id = "PAY-202607-$cleanUnit",
                    unitId = unitId,
                    condominiumId = condominiumId,
                    receiptFolio = "REC-20260703-8831",
                    concept = "Cuota Ordinaria Mantenimiento - Julio 2026",
                    amount = 1450.00,
                    paymentDateMillis = System.currentTimeMillis() - (69 * 24 * 3600 * 1000L), // hace 2 meses
                    paymentMethod = "Terminal Bancaria en Garita",
                    status = "LIQUIDADO",
                    bankReference = "POS-SANT-110293",
                    digitalSignatureSha = "SHA256:9A88F4102BECC1200FF5",
                    breakdownCuota = 1200.0,
                    breakdownFondoReserva = 150.0,
                    breakdownAmenidades = 100.0
                )
            )
            paymentDao.insertPayments(samplePayments)
        }
    }
}
