package com.example.esp32control

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), TcpClient.Listener {

    private lateinit var editIp: EditText
    private lateinit var editPort: EditText
    private lateinit var btnConnect: Button
    private lateinit var txtStatus: TextView
    private lateinit var txtSpeedLabel: TextView
    private lateinit var seekSpeed: SeekBar
    private lateinit var joystick: JoystickView
    private lateinit var btnForward: Button
    private lateinit var btnBackward: Button
    private lateinit var btnLeft: Button
    private lateinit var btnRight: Button
    private lateinit var btnStop: Button
    private lateinit var btnLedOn: Button
    private lateinit var btnLedOff: Button
    private lateinit var btnForkUp: Button
    private lateinit var btnForkDown: Button
    private lateinit var btnCamA: Button
    private lateinit var btnCamB: Button
    private lateinit var txtLogInfo: TextView

    private lateinit var db: DbHelper
    private var client: TcpClient? = null

    // Joystick state / rate limit
    private val uiHandler = Handler(Looper.getMainLooper())
    private var joyPressed = false
    private var joyDx = 0f
    private var joyDy = 0f
    private var joyNorm = 0f

    private var lastDirSent: String = "stop"
    private var lastSpeedSent: Int = -1
    private val sendIntervalMs = 80L
    private val speedDeltaMin = 5

    private val joyTicker = object : Runnable {
        override fun run() {
            if (joyPressed) {
                val desired = directionFrom(joyDx, joyDy, joyNorm)
                val base = seekSpeed.progress
                val targetSpeed = ((base * joyNorm).roundToInt()).coerceIn(0, 1023)

                if (abs(targetSpeed - lastSpeedSent) >= speedDeltaMin) {
                    sendCmd("set_speed:$targetSpeed")
                    lastSpeedSent = targetSpeed
                }
                if (desired != lastDirSent) {
                    if (desired == "stop") sendCmd("stop") else sendCmd(desired)
                    lastDirSent = desired
                }
                uiHandler.postDelayed(this, sendIntervalMs)
            } else {
                // si se soltó el joystick y quedó moviéndose -> STOP
                if (lastDirSent != "stop") {
                    sendCmd("stop")
                    lastDirSent = "stop"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = DbHelper(this)

        editIp = findViewById(R.id.editIp)
        editPort = findViewById(R.id.editPort)
        btnConnect = findViewById(R.id.btnConnect)
        txtStatus = findViewById(R.id.txtStatus)
        txtSpeedLabel = findViewById(R.id.txtSpeedLabel)
        seekSpeed = findViewById(R.id.seekSpeed)
        joystick = findViewById(R.id.joystick)
        btnForward = findViewById(R.id.btnForward)
        btnBackward = findViewById(R.id.btnBackward)
        btnLeft = findViewById(R.id.btnLeft)
        btnRight = findViewById(R.id.btnRight)
        btnStop = findViewById(R.id.btnStop)
        btnLedOn = findViewById(R.id.btnLedOn)
        btnLedOff = findViewById(R.id.btnLedOff)
        btnForkUp = findViewById(R.id.btnForkUp)
        btnForkDown = findViewById(R.id.btnForkDown)
        btnCamA = findViewById(R.id.btnCamA)
        btnCamB = findViewById(R.id.btnCamB)
        txtLogInfo = findViewById(R.id.txtLogInfo)

        // Cargar ajustes guardados
        db.getSetting("ip")?.let { editIp.setText(it) }
        db.getSetting("port")?.let { editPort.setText(it) }
        updateLogInfo()

        btnConnect.setOnClickListener {
            if (client == null) connect() else disconnect()
        }

        // Velocidad base
        txtSpeedLabel.text = "Velocidad (0..1023): ${seekSpeed.progress}"
        seekSpeed.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                txtSpeedLabel.text = "Velocidad (0..1023): $progress"
                // Si joystick no está presionado, permite setear velocidad manual
                if (!joyPressed && client != null) {
                    sendCmd("set_speed:$progress")
                    lastSpeedSent = progress
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Joystick
        joystick.setOnMoveListener(object : JoystickView.OnMoveListener {
            override fun onMove(x: Float, y: Float, norm: Float, isPressed: Boolean) {
                // Nota: y pantalla hacia abajo es positivo, invertimos eje Y para "arriba = forward"
                joyDx = x
                joyDy = -y
                joyNorm = norm
                if (isPressed && client != null) {
                    if (!joyPressed) {
                        joyPressed = true
                        uiHandler.post(joyTicker)
                    }
                } else {
                    joyPressed = false
                }
            }
        })

        // Botones de movimiento
        btnForward.setOnClickListener { sendCmd("forward"); lastDirSent = "forward" }
        btnBackward.setOnClickListener { sendCmd("backward"); lastDirSent = "backward" }
        btnLeft.setOnClickListener { sendCmd("left"); lastDirSent = "left" }
        btnRight.setOnClickListener { sendCmd("right"); lastDirSent = "right" }

        // Acciones
        btnStop.setOnClickListener { sendCmd("stop"); lastDirSent = "stop" }
        btnLedOn.setOnClickListener { sendCmd("led_on") }
        btnLedOff.setOnClickListener { sendCmd("led_off") }
        btnForkUp.setOnClickListener { sendCmd("forklift_up") }
        btnForkDown.setOnClickListener { sendCmd("forklift_down") }
        btnCamA.setOnClickListener { sendCmd("cam_a") }
        btnCamB.setOnClickListener { sendCmd("cam_b") }
        findViewById<Button>(R.id.btnViewLogs).setOnClickListener {
            startActivity(android.content.Intent(this, LogsActivity::class.java))
        }

    }

    private fun connect() {
        val ip = editIp.text.toString().trim()
        val port = editPort.text.toString().trim().toIntOrNull() ?: 12345
        db.setSetting("ip", ip)
        db.setSetting("port", port.toString())
        updateLogInfo()

        btnConnect.isEnabled = false
        txtStatus.text = "Conectando..."

        client = TcpClient(this).also { it.connect(ip, port) }
    }

    private fun disconnect() {
        joyPressed = false
        client?.disconnect()
        client = null
    }

    private fun sendCmd(cmd: String) {
        client?.sendLine(cmd)
        db.insertLog(cmd)
        updateLogInfo()
    }

    private fun updateLogInfo() {
        txtLogInfo.text = "Logs: ${db.countLogs()}"
    }

    private fun directionFrom(dx: Float, dy: Float, norm: Float): String {
        if (norm < 0.08f) return "stop"
        return if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
            if (dx > 0) "right" else "left"
        } else {
            if (dy > 0) "forward" else "backward"
        }
    }

    // TcpClient.Listener
    override fun onConnected() {
        runOnUiThread {
            txtStatus.text = "Conectado"
            btnConnect.isEnabled = true
            btnConnect.text = "Desconectar"
        }
    }

    override fun onDisconnected() {
        runOnUiThread {
            txtStatus.text = "Desconectado"
            btnConnect.text = "Conectar"
            btnConnect.isEnabled = true
        }
    }

    override fun onMessage(msg: String) {
        // El firmware puede responder "OK"
        // Podrías mostrar un Toast si quieres confirmar ACK
        // runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    override fun onError(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            txtStatus.text = "Error"
            btnConnect.isEnabled = true
        }
    }

    override fun onPause() {
        super.onPause()
        // seguridad: detener y desconectar
        sendCmd("stop")
        disconnect()
    }
}
