package com.example.data.location

/**
 * Constantes y Parámetros Operativos del Sistema de Geofencing y Proximidad
 * para "Los Prados Residencial" (Piloto MEDUSA OS v3).
 *
 * Principio: "TIEMPO = FAMILIA" (Apertura instantánea sin burocracia humana).
 */
object PradosLocationConstants {

    const val CONDOMINIO_ID = "PILOTO_PRADOS_RESIDENCIAL"
    const val CONDOMINIO_NAME = "Los Prados Residencial"
    const val ADDRESS = "Av. de la Cantera 2750, Código Postal 76116, Santiago de Querétaro, Qro."

    // Coordenadas Centrales de la Garita y Portón de Acceso Principal
    const val ACCESS_LATITUDE = 20.643658
    const val ACCESS_LONGITUDE = -100.492812

    // Radios de Acción Operativa
    const val BROAD_RADIUS_METERS = 500f // Radio Amplio: Pre-autorización y alerta pasiva en caseta
    const val SHORT_RADIUS_METERS = 50f  // Radio Corto: Activación de apertura automática por BLE / Wi-Fi

    // Identificadores de Geofences para Google Play Services Location
    const val GEOFENCE_BROAD_ID = "PRADOS_GEOFENCE_BROAD_500M"
    const val GEOFENCE_SHORT_ID = "PRADOS_GEOFENCE_SHORT_50M"

    // Acciones de Broadcast e Intents
    const val ACTION_GEOFENCE_EVENT = "com.example.medusa.ACTION_GEOFENCE_EVENT"
    const val ACTION_START_PROXIMITY_SERVICE = "com.example.medusa.ACTION_START_PROXIMITY_SERVICE"
    const val ACTION_STOP_PROXIMITY_SERVICE = "com.example.medusa.ACTION_STOP_PROXIMITY_SERVICE"
    const val ACTION_TRIGGER_MANUAL_OPEN = "com.example.medusa.ACTION_TRIGGER_MANUAL_OPEN"
    const val ACTION_SIMULATE_APPROACH = "com.example.medusa.ACTION_SIMULATE_APPROACH"

    // Canales de Notificación Android
    const val NOTIFICATION_CHANNEL_ID = "prados_proximity_location_channel"
    const val NOTIFICATION_CHANNEL_NAME = "Monitoreo de Proximidad Garita (Los Prados)"
    const val NOTIFICATION_CHANNEL_DESC = "Servicio en segundo plano para apertura automática de portón por proximidad (GPS, BLE y Wi-Fi)"

    const val GATE_OPENED_CHANNEL_ID = "prados_gate_opened_channel"
    const val GATE_OPENED_CHANNEL_NAME = "Apertura Automática de Portón"
    const val GATE_OPENED_CHANNEL_DESC = "Avisos de apertura exitosa del portón vehicular al entrar en radio corto (50m)"

    const val FOREGROUND_NOTIFICATION_ID = 8801
    const val GATE_OPENED_NOTIFICATION_ID = 8802

    // Parámetros de Hardware BLE & Wi-Fi
    const val GATE_BLE_SERVICE_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb"
    const val GATE_BLE_CHAR_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb"
    const val GATE_BLE_DEVICE_NAME = "PRADOS_GATE_BLE"

    const val GATE_WIFI_SSID_PREFIX = "PradosResidencial_Garita"
    const val GATE_WIFI_CONTROLLER_IP = "192.168.1.200"
    const val GATE_WIFI_CONTROLLER_PORT = 8266

    // Cooldown para evitar disparos múltiples si el usuario permanece estacionado cerca (3 minutos)
    const val GATE_OPENING_COOLDOWN_MILLIS = 3 * 60 * 1000L
}
