package com.example.airaligner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private val beepEngine = AudioBeepEngine()
    private val apiClient = AirOsClient("192.168.1.20") // Стандартный IP LiteBeam

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                AlignerScreen(apiClient, beepEngine)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        beepEngine.stop()
    }
}

@Composable
fun AlignerScreen(apiClient: AirOsClient, beepEngine: AudioBeepEngine) {
    var status by remember { mutableStateOf(DeviceStatus(-99, -99, -99, 0, false)) }
    var peakDbm by remember { mutableIntStateOf(-99) }
    var isSoundEnabled by remember { mutableStateOf(false) }

    // Опрос устройства каждые 150 мс
    LaunchedEffect(Unit) {
        while (true) {
            val newStatus = apiClient.fetchStatus()
            status = newStatus
            
            if (newStatus.isConnected && newStatus.signalDbm > peakDbm) {
                peakDbm = newStatus.signalDbm
            }
            
            if (isSoundEnabled && newStatus.isConnected) {
                beepEngine.updateSignal(newStatus.signalDbm)
            }
            delay(150)
        }
    }

    val delta = abs(status.chain0Dbm - status.chain1Dbm)
    val deltaColor = when {
        delta <= 2 -> Color(0xFF4CAF50) // Зеленый — отличный баланс
        delta <= 4 -> Color(0xFFFFEB3B) // Желтый — приемлемо
        else -> Color(0xFFF44336)       // Красный — сильный перекос!
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Статус подключения
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (status.isConnected) "ОНЛАЙН (192.168.1.20)" else "ПОИСК УСТРОЙСТВА...",
                color = if (status.isConnected) Color.Green else Color.Red,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = {
                peakDbm = -99 
            }) {
                Text("Сброс Peak")
            }
        }

        // Основной уровень сигнала
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${status.signalDbm}",
                fontSize = 96.sp,
                fontWeight = FontWeight.Black,
                color = Color.Yellow
            )
            Text(
                text = "dBm (ТЕКУЩИЙ)",
                fontSize = 18.sp,
                color = Color.LightGray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "PEAK: $peakDbm dBm",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Cyan
            )
        }

        // Метрики чейнов (Chain0 / Chain1) и Перекос
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Chain V: ${status.chain0Dbm} dBm", color = Color.White, fontSize = 18.sp)
                    Text("Chain H: ${status.chain1Dbm} dBm", color = Color.White, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ПЕРЕКОС (DELTA):", color = Color.Gray, fontSize = 16.sp)
                    Text(
                        text = "$delta dB",
                        color = deltaColor,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text("CINR / SNR: ${status.cinr} dB", color = Color.LightGray, fontSize = 14.sp)
            }
        }

        // Переключатель звукового маяка
        Button(
            onClick = {
                isSoundEnabled = !isSoundEnabled
                if (isSoundEnabled) beepEngine.start() else beepEngine.stop()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSoundEnabled) Color(0xFFFF9800) else Color(0xFF333333)
            )
        ) {
            Text(
                text = if (isSoundEnabled) "🔊 ЗВУКОВОЙ МАЯК: ВКЛ" else "🔇 ЗВУКОВОЙ МАЯК: ВЫКЛ",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
