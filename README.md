# مكتبة القدس الجديدة

المستودع الرسمي لتطبيق مكتبة القدس الجديدة.

سيتم اعتماد GitHub Actions كبوابة بناء وفحص، ومراجعة المصادر والروابط الخارجية قبل التسليم.


## التطوير الحالي

تطبيق Android أصلي تجريبي، يستكمل الهيكل الموجود في `dev/release-gate`.
يوفر فهرساً محلياً، بحثاً عربياً بالبيانات الوصفية، تفاصيل سجل، مفضلة على الجهاز، ووضعاً ليلياً ونهارياً. يوجد سجل توضيحي واحد معلن بوضوح، ولا توجد ملفات كتب أو حسابات أو مزامنة بعد.

### التشغيل والفحص

المتطلبات: JDK 17، Gradle 8.9، Android SDK 35. لا يتوفر Gradle Wrapper حالياً؛ استخدم نسخة Gradle المحددة.

```sh
python3 -m unittest discover -s tests -v
gradle --no-daemon testDebugUnitTest assembleDebug lintDebug
# With a running emulator/device (API 24 or 35):
gradle --no-daemon connectedDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

راجع [التقرير الفني](docs/TECHNICAL_AUDIT.md) و[سجل المراحل](DEVELOPMENT_PROGRESS.md). نجاح اختبارات Python لا يعني نجاح بناء Android. النسخة ليست إصدار إنتاج، ولا تُرسل استعلامات البحث أو المفضلة خارج الجهاز.

اختبارات `CatalogDatabaseTest` تنشئ قاعدة عشوائية مستقلة لكل اختبار وتحذف تلك القاعدة فقط. تفحص الإنشاء والعلاقات والقيود واستمرار البيانات والبحث الفعلي على Android. سير `Android Device Tests` يشغلها على API 24 و35 ويكتب النتائج في ملخص المهمة دون الاعتماد على مساحة artifacts. يظل رفع APK في بوابة البناء مطلوباً ومستقلاً.

تضيف `MainActivityTest` ثلاث حالات لإعادة إنشاء الشاشة والثيم واستعادة البحث. شغّل اختبارات الواجهة على جهاز اختبار مخصص؛ تعيد قيمة الثيم السابقة ولا تحذف بيانات الفهرس. آخر تحقق موثق: 14 اختبار Android على كل من API 24 و35؛ تفاصيل الأدلة والحدود في سجل المراحل.

### اختبار توقف العملية (محاكي اختبار مخصص فقط)

```sh
gradle --no-daemon connectedDebugAndroidTest
python3 tests/run_process_restart.py
```

يشغّل Gradle الاختبارات العادية ويستبعد `ProcessRestartProbe`. يشغّل البرنامج حالتي prepare/verify في عمليتين منفصلتين مع force-stop بينهما ويؤكد اختلاف PID. هذه تجربة استمرار الثيم والمفضلة والفهرس؛ ليست اختبار استعادة البحث بعد الإيقاف الإجباري. يعيد التفضيلات السابقة، ولا يحذف الفهرس. لا تشغّل prepare/verify منفردتين أو بترتيب عشوائي.

### أحدث حزمة اختبار متحقَّق منها

[تنزيل ZIP الذي يحتوي APK debug](https://github.com/nexvary/AlQuds-New-Library/actions/runs/41503835976/artifacts/13169907219) — للالتزام `240cc22`، متاح حتى 2027-01-31 وقد يتطلب تسجيل دخول GitHub. هذه حزمة اختبار للتطبيق الموجود في هذا الفرع، وليست واجهة النسخة القديمة المصورة ولا إصدار إنتاج. نجح البناء وlint وJUnit، و25 اختبار Python، و16 حالة Android واختبارا force-stop على كل من API 24 و35.

راجع [سياسة الموارد والأسرار](SECURITY.md). لتشغيل فحصها محلياً بعد إضافة الملفات المراد فحصها إلى Git:

```sh
python3 tools/check_source_policy.py
```

### النسخة التجريبية الحالية: 1.4.1

[تنزيل ZIP يحتوي APK debug 1.4.1](https://github.com/nexvary/AlQuds-New-Library/actions/runs/41513381106/artifacts/13174841356). استُبدلت طريقة حفظ المفضلة بتخزين SQLite مع استيراد القيم القديمة مرة واحدة دون حذفها، وأصبحت المفضلة تُصفّى قبل حد 100 نتيجة. راجع [خطة ترحيل البيانات](docs/DATA_MIGRATION.md) و[خط أساس Android](docs/ANDROID_BASELINE.md) قبل الرجوع إلى APK أقدم.

التحقق: 28 اختبار Python، و5 حالات JUnit، و25 حالة Android واختبارا force-stop على كل من API 24 و35، مع نجاح البناء وlint ورفع الحزمة. لا تزال حزمة اختبار محلية وليست إصدار إنتاج.
