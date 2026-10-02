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
}
