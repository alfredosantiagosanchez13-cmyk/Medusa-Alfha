package com.example.data.finance

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Entidad de persistencia en Room para el Histórico de Cuotas de Mantenimiento y Recibos Digitales.
 * Permite la consulta offline garantizada y la descarga/visualización de comprobantes criptográficos.
 */
@Entity(
    tableName = "maintenance_payments",
    indices = [
        Index(value = ["unitId"]),
        Index(value = ["receiptFolio"], unique = true),
        Index(value = ["paymentDateMillis"])
    ]
)
data class MaintenancePaymentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // ej: "PAY-2026-09-01"

    @ColumnInfo(name = "unitId")
    val unitId: String, // ej: "Casa 104", "Casa 01"

    @ColumnInfo(name = "condominiumId")
    val condominiumId: String,

    @ColumnInfo(name = "receiptFolio")
    val receiptFolio: String, // ej: "REC-2026-09-7812"

    @ColumnInfo(name = "concept")
    val concept: String, // ej: "Cuota Ordinaria Mantenimiento - Septiembre 2026"

    @ColumnInfo(name = "amount")
    val amount: Double, // 1450.00

    @ColumnInfo(name = "paymentDateMillis")
    val paymentDateMillis: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "paymentMethod")
    val paymentMethod: String, // "SPEI Transferencia", "Tarjeta de Débito/Crédito", "En Garita / Terminal"

    @ColumnInfo(name = "status")
    val status: String = "LIQUIDADO", // "LIQUIDADO", "APLICADO", "CONCILIADO"

    @ColumnInfo(name = "bankReference")
    val bankReference: String = "SPEI-BBVA-984210",

    @ColumnInfo(name = "digitalSignatureSha")
    val digitalSignatureSha: String = "SHA256:7B8F9A02C1E34D99FA21",

    @ColumnInfo(name = "breakdownCuota")
    val breakdownCuota: Double = 1200.0,

    @ColumnInfo(name = "breakdownFondoReserva")
    val breakdownFondoReserva: Double = 150.0,

    @ColumnInfo(name = "breakdownAmenidades")
    val breakdownAmenidades: Double = 100.0
) {
    val formattedDate: String
        get() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(paymentDateMillis))

    val formattedAmount: String
        get() = String.format(Locale.US, "$%,.2f MXN", amount)
}
