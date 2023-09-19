package com.petpal.mungmate.ui.chat

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.petpal.mungmate.databinding.FragmentWalkMateRequestBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WalkMateRequestFragment : Fragment() {
    private var _fragmentWalkMateRequestBinding : FragmentWalkMateRequestBinding? = null
    private val fragmentWalkMateRequestBinding get() = _fragmentWalkMateRequestBinding!!

    lateinit var senderId: String
    lateinit var receiverId: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _fragmentWalkMateRequestBinding = FragmentWalkMateRequestBinding.inflate(inflater)

        // 채팅창에서 전달받은 산책 메이트 요청 송신자 -> 수신자 id
        val args = WalkMateRequestFragmentArgs.fromBundle(requireArguments())
        senderId = args.senderId
        receiverId = args.receiverId

        return fragmentWalkMateRequestBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragmentWalkMateRequestBinding.run {
            toolbarWalkMateRequest.setNavigationOnClickListener {
                findNavController().popBackStack()
            }

            buttonRequest.setOnClickListener {
                // TODO 산책 매칭 메시지 저장

                // TODO 채팅방으로 돌아가면서 데이터 전달하기
                findNavController().popBackStack()
            }

            textInputEditTextDate.setOnClickListener {
                // DatePicker 기본값 오늘로 설정
                val datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("날짜 선택")
                    .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                    .build()

                datePicker.addOnPositiveButtonClickListener { selectedDateInMillis ->
                    val selectDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(Date(selectedDateInMillis))
                    textInputEditTextDate.setText(selectDate)
                }
                datePicker.show(parentFragmentManager, "tag")
            }

            textInputEditTextTime.setOnClickListener {
                val timePicker = MaterialTimePicker.Builder()
                    .setTitleText("시간 선택")
                    .setTimeFormat(TimeFormat.CLOCK_12H)
                    .build()
                timePicker.addOnPositiveButtonClickListener {
                    val selectedTime = String.format("%02d:%02d", timePicker.hour, timePicker.minute)
                    textInputEditTextTime.setText(selectedTime)
                }
                timePicker.show(parentFragmentManager, "tag")
            }

        }
    }

}