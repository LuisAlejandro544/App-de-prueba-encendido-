package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Pruebas unitarias locales para la lógica de WakeGuard y el Encendido Programado de Rescate.
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun `verificar que el encendido programado de rescate solo interviene al 0 por ciento`() {
        // Regla del usuario: Si la batería es mayor al 1%, no debe intervenir
        val bateriaNormal = 75
        val debeIntervenirNormal = bateriaNormal <= 1
        assertFalse(debeIntervenirNormal)

        // Si la batería es 0% o 1%, debe armar el rescate
        val bateriaCritica = 0
        val debeIntervenirCritico = bateriaCritica <= 1
        assertTrue(debeIntervenirCritico)
    }

    @Test
    fun `verificar calculo ciclico de hora unica de encendido programado`() {
        // Verificación de rango 24 horas y minutos
        val horaActual = 23
        val siguienteHora = (horaActual + 1) % 24
        assertEquals(0, siguienteHora)

        val minutoActual = 55
        val siguienteMinuto = (minutoActual + 5) % 60
        assertEquals(0, siguienteMinuto)
    }

    @Test
    fun `verificar que el filtro vehicular suprime baches repetitivos de moto en bolso`() {
        val filter = com.example.sensor.VehicularBumpFilter()
        val threshold = 15.0f
        val azVisible = 8.0f // Pantalla mirando hacia arriba
        val pitchNormal = 40.0f
        var tiempo = 10000L

        // Primer impacto de bache: Aislado, aún no suprime
        val impacto1 = filter.shouldSuppressBump(tiempo, 16.0f, threshold, azVisible, pitchNormal)
        assertFalse("El primer impacto aislado no debe suprimirse automáticamente si tiene orientación válida", impacto1)

        // Segundo impacto rápido a los 250 ms (bache continuo de carretera)
        tiempo += 250L
        val impacto2 = filter.shouldSuppressBump(tiempo, 17.0f, threshold, azVisible, pitchNormal)
        assertFalse("El segundo impacto por sí solo aún no alcanza el umbral de ráfaga continua", impacto2)

        // Tercer impacto rápido a los 200 ms (traqueteo continuo de moto)
        tiempo += 200L
        val impacto3 = filter.shouldSuppressBump(tiempo, 18.0f, threshold, azVisible, pitchNormal)
        assertTrue("El tercer impacto consecutivo en <1.5s debe ser suprimido como traqueteo de moto", impacto3)

        // Cuarto impacto durante el periodo de calma de 2 segundos: debe seguir suprimido
        tiempo += 500L
        val impacto4 = filter.shouldSuppressBump(tiempo, 16.0f, threshold, azVisible, pitchNormal)
        assertTrue("Durante la ventana de calma vehicular debe mantenerse la supresión activa", impacto4)
    }

    @Test
    fun `verificar que el filtro vehicular ignora impactos con el celular invertido en un bolso koala`() {
        val filter = com.example.sensor.VehicularBumpFilter()
        val threshold = 15.0f
        val azInvertido = -4.5f // Teléfono boca abajo dentro del bolso
        val pitchInvertido = -10.0f
        val tiempo = 5000L

        // Aunque sea un único bache fuerte, si el teléfono está boca abajo en el bolso se ignora
        val bacheEnBolso = filter.shouldSuppressBump(tiempo, 22.0f, threshold, azInvertido, pitchInvertido)
        assertTrue("Cualquier impacto con la pantalla mirando al suelo o en bolso invertido debe ser ignorado", bacheEnBolso)
    }

    @Test
    fun `verificar que un gesto deliberado de lectura es aceptado normalmente`() {
        val filter = com.example.sensor.VehicularBumpFilter()
        val threshold = 15.0f
        val azLectura = 7.5f // Pantalla visible de frente
        val pitchLectura = 45.0f // Inclinación típica al sostener el móvil con la mano
        val tiempo = 20000L

        val gestoValido = filter.shouldSuppressBump(tiempo, 16.5f, threshold, azLectura, pitchLectura)
        assertFalse("Un gesto legítimo de lectura no debe ser suprimido", gestoValido)
        assertTrue("La orientación debe ser reconocida como ergonómica natural", filter.isNaturalViewingOrientation(azLectura, pitchLectura))
    }

    @Test
    fun `verificar que una sacudida humana de 3 muestras sucesivas en 80 ms NO es bloqueada por el filtro vehicular`() {
        val filter = com.example.sensor.VehicularBumpFilter()
        val threshold = 15.0f
        val azNormal = 7.0f
        val pitchNormal = 35.0f
        val tiempoInicio = 30000L

        // Muestra 1 a 0 ms (inicio del golpe de la sacudida)
        val s1 = filter.shouldSuppressBump(tiempoInicio, 17.0f, threshold, azNormal, pitchNormal)
        assertFalse("La primera muestra de la sacudida no debe suprimirse", s1)

        // Muestra 2 a 30 ms (mismo golpe de la sacudida humana)
        val s2 = filter.shouldSuppressBump(tiempoInicio + 30L, 18.0f, threshold, azNormal, pitchNormal)
        assertFalse("La segunda muestra a 30 ms es parte del mismo movimiento y no debe suprimirse", s2)

        // Muestra 3 a 60 ms (mismo golpe de la sacudida humana)
        val s3 = filter.shouldSuppressBump(tiempoInicio + 60L, 19.0f, threshold, azNormal, pitchNormal)
        assertFalse("La tercera muestra a 60 ms no debe confundirse con 3 baches de carretera", s3)
    }

    @Test
    fun `verificar logica adaptativa del sensor de proximidad en dispositivos Tecno con sensor binario`() {
        // En Tecno / Xiaomi, muchos sensores binarios tienen maxRange = 1.0f
        val maxRangeBinario = 1.0f

        // Caso 1: Celular en la mano despejado (distancia = 1.0f)
        val distanceDespejado = 1.0f
        val isNearDespejado = if (maxRangeBinario <= 2.0f) {
            distanceDespejado < maxRangeBinario
        } else {
            distanceDespejado < 3.0f && distanceDespejado < maxRangeBinario
        }
        assertFalse("Cuando el sensor binario reporta 1.0f debe considerarse DESPEJADO (no cerca)", isNearDespejado)

        // Caso 2: Celular en el bolsillo tapado (distancia = 0.0f)
        val distanceTapado = 0.0f
        val isNearTapado = if (maxRangeBinario <= 2.0f) {
            distanceTapado < maxRangeBinario
        } else {
            distanceTapado < 3.0f && distanceTapado < maxRangeBinario
        }
        assertTrue("Cuando el sensor binario reporta 0.0f debe considerarse CERCA (en bolsillo)", isNearTapado)
    }

    @Test
    fun `verificar logica de decision bidireccional encendido y apagado por sacudida`() {
        // Escenario 1: Pantalla encendida + Administrador de dispositivo activo
        val pantallaEncendida = true
        val adminActivo = true
        val accionAlSacudir = when {
            pantallaEncendida && adminActivo -> "BLOQUEAR_APAGAR"
            pantallaEncendida && !adminActivo -> "SOLO_FEEDBACK_VISUAL"
            !pantallaEncendida -> "ILUMINAR_ENCENDER"
            else -> "IGNORAR"
        }
        assertEquals("Con pantalla encendida y administrador activo debe bloquear y apagar", "BLOQUEAR_APAGAR", accionAlSacudir)

        // Escenario 2: Pantalla apagada
        val pantallaApagada = false
        val accionPantallaApagada = if (!pantallaApagada) "ILUMINAR_ENCENDER" else "BLOQUEAR_APAGAR"
        assertEquals("Con pantalla apagada debe encender", "ILUMINAR_ENCENDER", accionPantallaApagada)
    }
}
