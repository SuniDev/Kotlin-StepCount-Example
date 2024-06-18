package com.example.stepscountexample

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import com.example.stepscountexample.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val PERMISSIONS =
        setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getWritePermission(StepsRecord::class)
        )
    private lateinit var healthConnectClient: HealthConnectClient
    private lateinit var requestPermissions: ActivityResultLauncher<Set<String>>
    private var isTappedGetSteps: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 권한 요청 런처 생성
        val requestPermissionActivityContract = PermissionController.createRequestPermissionResultContract()

        requestPermissions = registerForActivityResult(requestPermissionActivityContract) { granted ->
            if (granted.containsAll(PERMISSIONS)) {
                // 권한이 성공적으로 부여됨
                startSteps()
            } else {
                // TODO 필요한 권한이 부족한 경우 처리
            }
        }


        val providerPackageName = "com.google.android.apps.healthdata"
        binding.btnGetSteps.setOnClickListener {
            isTappedGetSteps = true
            getHealthConnectClient(this, providerPackageName)
        }
        binding.btnWriteSteps.setOnClickListener {
            isTappedGetSteps = false
            getHealthConnectClient(this, providerPackageName)
        }
    }

    private fun getHealthConnectClient(context: Context, providerPackageName: String) {
        // SDK 상태 확인
        val availabilityStatus = HealthConnectClient.getSdkStatus(context, providerPackageName)

        when (availabilityStatus) {
            HealthConnectClient.SDK_UNAVAILABLE -> {
                // TODO: SDK 사용 불가 처리
                // early return as there is no viable integration
                return
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                // 패키지 설치 유도
                // Optionally redirect to package installer to find a provider
                val uriString = "market://details?id=$providerPackageName&url=healthconnect%3A%2F%2Fonboarding"
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setPackage("com.android.vending")
                    data = Uri.parse(uriString)
                    putExtra("overlay", true)
                    putExtra("callerId", context.packageName)
                }
                context.startActivity(intent)
                return
            }
            else -> {
                // Proceed with using healthConnectClient for further operations
                // 권한 있음
                healthConnectClient = HealthConnectClient.getOrCreate(context)
                // 권한 확인 및 실행
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        checkPermissionsAndRun(requestPermissions)
                    } catch (e: Exception) {
                        // TODO 오류 처리
                    }
                }
            }
        }
    }

    private suspend fun checkPermissionsAndRun(requestPermissions: ActivityResultLauncher<Set<String>>) {
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        if (granted.containsAll(PERMISSIONS)) {
            // 권한이 이미 부여됨; 데이터를 읽거나 삽입하는 작업 수행
            startSteps()
        } else {
            // 권한 요청 런처 실행
            requestPermissions.launch(PERMISSIONS)
        }
    }

    private fun startSteps() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (isTappedGetSteps) {
                    aggregateSteps()
                } else {
                    insertSteps()
                }
            } catch (e: Exception) {
                // TODO 오류 처리
            }
        }
    }

    private suspend fun aggregateSteps() {
        try {
            // 오늘 걸음 수 측정
            val now = ZonedDateTime.now()
            val startTime = now.withHour(0).withMinute(0).withSecond(0).toInstant()
            val endTime = now.toInstant()

            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = androidx.health.connect.client.time.TimeRangeFilter.between(
                        startTime = startTime,
                        endTime = endTime
                    )
                )
            )

            // 결과가 없을 경우 null 일 수 있음
            val stepCount = response[StepsRecord.COUNT_TOTAL] ?: 0
            println("Today's step count: $stepCount")
            binding.tvStepsCount.text = "걸음 수 : $stepCount"
        } catch (e: Exception) {
            // 오류 처리
            e.printStackTrace()
        }
    }

    private suspend fun insertSteps() {
        // TEST 용 걸음 수 쓰기 함수
        try {
            // 더미 데이터 포인트 생성
            val stepsRecords = mutableListOf<StepsRecord>()

            val now = ZonedDateTime.now()
            val startTime1 = now.minusSeconds(60).toInstant()
            val endTime1 = now.minusSeconds(30).toInstant()
            val startTime2 = endTime1
            val endTime2 = now.toInstant()

            val stepsRecord1 = StepsRecord(
                count = 2300,
                startTime = startTime1,
                endTime = endTime1,
                startZoneOffset = ZoneId.systemDefault().rules.getOffset(startTime1),
                endZoneOffset = ZoneId.systemDefault().rules.getOffset(endTime1),
            )
            val stepsRecord2 = StepsRecord(
                count = 1000,
                startTime = startTime2,
                endTime = endTime2,
                startZoneOffset = ZoneId.systemDefault().rules.getOffset(startTime2),
                endZoneOffset = ZoneId.systemDefault().rules.getOffset(endTime2),
            )

            stepsRecords.add(stepsRecord1)
            stepsRecords.add(stepsRecord2)

            healthConnectClient.insertRecords(stepsRecords)
        } catch (e: Exception) {
            // Run error handling here
        }
    }
}
