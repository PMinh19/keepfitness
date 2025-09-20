package com.example.keepyfitness

import com.example.keepyfitness.Model.FormFeedback
import com.example.keepyfitness.Model.FeedbackType
import com.example.keepyfitness.Model.FeedbackSeverity
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.*

class FormCorrector {

    companion object {
        private const val TAG = "FormCorrector"
    }

    fun analyzeForm(exerciseId: Int, pose: Pose): List<FormFeedback> {
        val feedbacks = mutableListOf<FormFeedback>()

        when (exerciseId) {
            1 -> feedbacks.addAll(analyzePushUpForm(pose))
            2 -> feedbacks.addAll(analyzeSquatForm(pose))
            3 -> feedbacks.addAll(analyzeJumpingJackForm(pose))
            4 -> feedbacks.addAll(analyzePlankForm(pose))
        }

        return feedbacks
    }

    fun calculateFormQuality(exerciseId: Int, pose: Pose): Int {
        val feedbacks = analyzeForm(exerciseId, pose)

        var baseScore = 100

        feedbacks.forEach { feedback ->
            when (feedback.severity) {
                FeedbackSeverity.CRITICAL -> baseScore -= 20
                FeedbackSeverity.WARNING -> baseScore -= 10
                FeedbackSeverity.INFO -> baseScore -= 5
            }
        }

        return maxOf(0, baseScore)
    }

    private fun analyzePushUpForm(pose: Pose): List<FormFeedback> {
        val feedbacks = mutableListOf<FormFeedback>()

        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)

        if (leftShoulder != null && rightShoulder != null &&
            leftElbow != null && rightElbow != null &&
            leftWrist != null && rightWrist != null &&
            leftHip != null && rightHip != null) {

            // Kiểm tra góc cùi chỏ
            val leftElbowAngle = calculateAngle(leftShoulder, leftElbow, leftWrist)
            val rightElbowAngle = calculateAngle(rightShoulder, rightElbow, rightWrist)

            // Kiểm tra cùi chỏ quá ra ngoài
            if (leftElbowAngle > 90 || rightElbowAngle > 90) {
                val wristShoulderDistance = distance(leftWrist, leftShoulder)
                val elbowShoulderDistance = distance(leftElbow, leftShoulder)

                if (wristShoulderDistance > elbowShoulderDistance * 1.5) {
                    feedbacks.add(FormFeedback(
                        exerciseId = 1,
                        feedbackType = FeedbackType.POSTURE_CORRECTION,
                        message = "Elbows too wide, keep elbows close to your body",
                        severity = FeedbackSeverity.WARNING
                    ))
                }
            }

            // Kiểm tra thẳng lưng
            val knee = leftKnee ?: rightKnee
            if (knee != null) {
                val torsoAngle = calculateAngle(leftShoulder, leftHip, knee)
                if (torsoAngle < 160) {
                    feedbacks.add(FormFeedback(
                        exerciseId = 1,
                        feedbackType = FeedbackType.POSTURE_CORRECTION,
                        message = "Keep your back straight, don't arch or bend",
                        severity = FeedbackSeverity.CRITICAL
                    ))
                }
            }

            // Kiểm tra tay không thẳng hàng
            val handAlignment = abs(leftWrist.position.y - rightWrist.position.y)
            if (handAlignment > 50) {
                feedbacks.add(FormFeedback(
                    exerciseId = 1,
                    feedbackType = FeedbackType.ALIGNMENT,
                    message = "Hands not aligned, place hands evenly",
                    severity = FeedbackSeverity.WARNING
                ))
            }

            // Feedback tích cực
            if (leftElbowAngle in 45.0..90.0 && rightElbowAngle in 45.0..90.0) {
                feedbacks.add(FormFeedback(
                    exerciseId = 1,
                    feedbackType = FeedbackType.POSTURE_CORRECTION,
                    message = "Perfect! Great elbow angle",
                    severity = FeedbackSeverity.INFO
                ))
            }
        }

        return feedbacks
    }

    private fun analyzeSquatForm(pose: Pose): List<FormFeedback> {
        val feedbacks = mutableListOf<FormFeedback>()

        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        if (leftHip != null && rightHip != null &&
            leftKnee != null && rightKnee != null &&
            leftAnkle != null && rightAnkle != null) {

            // Kiểm tra góc đầu gối
            val leftKneeAngle = calculateAngle(leftHip, leftKnee, leftAnkle)
            val rightKneeAngle = calculateAngle(rightHip, rightKnee, rightAnkle)

            // Kiểm tra đầu gối không vượt quá ngón chân
            if (leftKnee.position.x > leftAnkle.position.x + 30 ||
                rightKnee.position.x > rightAnkle.position.x + 30) {
                feedbacks.add(FormFeedback(
                    exerciseId = 2,
                    feedbackType = FeedbackType.SAFETY_WARNING,
                    message = "Knees past toes, push your hips back",
                    severity = FeedbackSeverity.CRITICAL
                ))
            }

            // Kiểm tra độ sâu squat
            val avgHipY = (leftHip.position.y + rightHip.position.y) / 2
            val avgKneeY = (leftKnee.position.y + rightKnee.position.y) / 2

            if (avgHipY < avgKneeY - 20) {
                feedbacks.add(FormFeedback(
                    exerciseId = 2,
                    feedbackType = FeedbackType.RANGE_OF_MOTION,
                    message = "Squat not deep enough, go lower",
                    severity = FeedbackSeverity.WARNING
                ))
            }

            // Kiểm tra chân không đều
            val footAlignment = abs(leftAnkle.position.x - rightAnkle.position.x)
            val shoulderWidth = distance(leftHip, rightHip)

            if (footAlignment > shoulderWidth * 1.5) {
                feedbacks.add(FormFeedback(
                    exerciseId = 2,
                    feedbackType = FeedbackType.ALIGNMENT,
                    message = "Feet too wide, bring them closer together",
                    severity = FeedbackSeverity.WARNING
                ))
            }

            // Feedback tích cực
            if (leftKneeAngle in 80.0..100.0 && rightKneeAngle in 80.0..100.0) {
                feedbacks.add(FormFeedback(
                    exerciseId = 2,
                    feedbackType = FeedbackType.POSTURE_CORRECTION,
                    message = "Perfect! Great squat angle",
                    severity = FeedbackSeverity.INFO
                ))
            }
        }

        return feedbacks
    }

    private fun analyzeJumpingJackForm(pose: Pose): List<FormFeedback> {
        val feedbacks = mutableListOf<FormFeedback>()

        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)

        if (leftWrist != null && rightWrist != null &&
            leftAnkle != null && rightAnkle != null &&
            leftShoulder != null && rightShoulder != null) {

            // Kiểm tra tay có giơ cao đủ không
            val avgShoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2
            val avgWristY = (leftWrist.position.y + rightWrist.position.y) / 2

            if (avgWristY > avgShoulderY - 20) {
                feedbacks.add(FormFeedback(
                    exerciseId = 3,
                    feedbackType = FeedbackType.RANGE_OF_MOTION,
                    message = "Raise your arms higher, above your head",
                    severity = FeedbackSeverity.WARNING
                ))
            }

            // Kiểm tra hai tay không đồng bộ
            val handSync = abs(leftWrist.position.y - rightWrist.position.y)
            if (handSync > 40) {
                feedbacks.add(FormFeedback(
                    exerciseId = 3,
                    feedbackType = FeedbackType.TIMING,
                    message = "Arms not synchronized, raise them together",
                    severity = FeedbackSeverity.WARNING
                ))
            }

            // Kiểm tra chân không tách đủ xa
            val footDistance = distance(leftAnkle, rightAnkle)
            val shoulderWidth = distance(leftShoulder, rightShoulder)

            if (footDistance < shoulderWidth * 1.2) {
                feedbacks.add(FormFeedback(
                    exerciseId = 3,
                    feedbackType = FeedbackType.RANGE_OF_MOTION,
                    message = "Spread your legs wider, beyond shoulder width",
                    severity = FeedbackSeverity.WARNING
                ))
            }

            // Feedback tích cực
            if (avgWristY < avgShoulderY - 50 && footDistance > shoulderWidth * 1.5) {
                feedbacks.add(FormFeedback(
                    exerciseId = 3,
                    feedbackType = FeedbackType.POSTURE_CORRECTION,
                    message = "Excellent! Perfect form",
                    severity = FeedbackSeverity.INFO
                ))
            }
        }

        return feedbacks
    }

    private fun analyzePlankForm(pose: Pose): List<FormFeedback> {
        val feedbacks = mutableListOf<FormFeedback>()

        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        if (leftShoulder != null && rightShoulder != null &&
            leftHip != null && rightHip != null &&
            leftAnkle != null && rightAnkle != null) {

            // Kiểm tra thẳng lưng
            val shoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2
            val hipY = (leftHip.position.y + rightHip.position.y) / 2
            val ankleY = (leftAnkle.position.y + rightAnkle.position.y) / 2

            val hipSag = abs(hipY - shoulderY)
            val ankleSag = abs(ankleY - shoulderY)

            if (hipSag > 40) {
                if (hipY > shoulderY) {
                    feedbacks.add(FormFeedback(
                        exerciseId = 4,
                        feedbackType = FeedbackType.POSTURE_CORRECTION,
                        message = "Hips too low, lift your hips up",
                        severity = FeedbackSeverity.CRITICAL
                    ))
                } else {
                    feedbacks.add(FormFeedback(
                        exerciseId = 4,
                        feedbackType = FeedbackType.POSTURE_CORRECTION,
                        message = "Hips too high, lower your hips down",
                        severity = FeedbackSeverity.CRITICAL
                    ))
                }
            }

            // Feedback tích cực
            if (hipSag < 30 && ankleSag < 30) {
                feedbacks.add(FormFeedback(
                    exerciseId = 4,
                    feedbackType = FeedbackType.POSTURE_CORRECTION,
                    message = "Excellent! Keep your body straight",
                    severity = FeedbackSeverity.INFO
                ))
            }
        }

        return feedbacks
    }

    private fun calculateAngle(first: PoseLandmark, mid: PoseLandmark, last: PoseLandmark): Double {
        val a = distance(mid, last)
        val b = distance(first, mid)
        val c = distance(first, last)
        return acos((b * b + a * a - c * c) / (2 * b * a)) * (180 / PI)
    }

    private fun distance(p1: PoseLandmark, p2: PoseLandmark): Double {
        val dx = p1.position.x - p2.position.x
        val dy = p1.position.y - p2.position.y
        return sqrt((dx * dx + dy * dy).toDouble())
    }
}
