package com.medo.aiassistant.ai

import com.medo.aiassistant.data.NotificationEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PromptBuilder {

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault())

    fun buildNotificationsText(notifications: List<NotificationEntity>): String {
        val sb = StringBuilder()
        for ((index, item) in notifications.withIndex()) {
            val formattedDate = timeFormat.format(Date(item.timestamp))
            sb.append("${index + 1}. [${item.appName}] ($formattedDate)\n")
            if (item.title.isNotEmpty()) sb.append("   المرسل / العنوان: ${item.title}\n")
            if (item.content.isNotEmpty()) sb.append("   النص: ${item.content}\n")
            sb.append("\n")
        }
        return sb.toString()
    }

    fun buildSummaryPrompt(notifications: List<NotificationEntity>): String {
        val text = buildNotificationsText(notifications)
        return """
أنت المساعد الذكي الشخصي لهاتف المستخدم (Medo AI Assistant).
إليك قائمة بجميع الإشعارات والرسائل التي استقبلها هاتف المستخدم حديثاً:

---
$text
---

المطلوب منك:
قدم تقريراً وملخصاً شاملاً، ذكياً، ومنظماً جداً باللغة العربية.
قم بتقسيم الملخص إلى الأقسام التالية مع استخدام الإيموجي:
1. 🚨 **تنبيهات وأمور عاجلة**: (مثل: رموز التحقق OTP، معاملات بنكية، مكالمات فائتة، رسائل حرجة).
2. 💬 **ملخص الرسائل والمحادثات**: (ماذا قال الأشخاص على واتساب، تيليجرام، SMS باختصار ودقة).
3. 🔔 **تحديثات التطبيقات الأخرى**: (إشعارات الأخبار، السوشيال ميديا، الإيميلات العامة).
4. 💡 **ملاحظة أو نصيحة سريعة**: أي نصيحة للمستخدم بخصوص ما استقبله هاتفه.

اجعل التقرير أنيقاً وواضحاً وسهل القراءة السريعة.
""".trimIndent()
    }

    fun buildTaskExtractionPrompt(notifications: List<NotificationEntity>): String {
        val text = buildNotificationsText(notifications)
        return """
أنت خبير ذكاء اصطناعي متخصص في تحليل رسائل الهاتف واستخراج "المهام والطلبات" (Action Items & Requests) الموجهة للمستخدم.
اقرأ الرسائل التالية بدقة:

---
$text
---

ابحث عن أي شيء يطلبه شخص أو تطبيق من المستخدم للقيام به أو الرد عليه (مثلاً: "ابعتلي ملف"، "كلمني"، "سدد الفاتورة"، "احضر الاجتماع"، "رد على الاستفسار"، "قم بتأكيد الحجز").

يجب أن تكون إجابتك عبارة عن كود JSON صالح فقط (Raw JSON Array)، بدون أي شرح قبله أو بعده، وبدون علامات تخفيض (markdown ```json)، بالصيغة التالية:
[
  {
    "requester": "اسم المرسل والتطبيق (مثال: أحمد - واتساب)",
    "taskDescription": "وصف دقيق للمهمة أو الشيء المطلوب من المستخدم إنجازه",
    "deadline": "الموعد المحدد إن وجد أو null",
    "isUrgent": true أو false حسب درجة العجلة
  }
]

إذا لم تجد أي طلبات أو مهام مطلوبة، أرجع فقط مصفوفة فارغة: []
""".trimIndent()
    }
}
