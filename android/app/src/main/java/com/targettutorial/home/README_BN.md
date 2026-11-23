# নতুন Kotlin ফাইল — Target Tutorial Home

এই ZIP-এ শুধু নতুন যোগ করার জন্য Kotlin source file আছে। এগুলো এখনো MainActivity navigation-এ যুক্ত করা হয়নি; পরের ধাপে existing file update করে screens wire করতে হবে।

## Files
- StudentIdCardScreen.kt
- OfflineMaterialsManager.kt
- StudentSettingsScreen.kt
- AchievementScreen.kt
- NotificationScreen.kt
- LocalReminderWorker.kt

## প্রয়োজনীয় Gradle dependencies
`android/app/build.gradle`-এর dependencies block-এ পরের ধাপে যোগ করতে হবে:

```gradle
implementation 'com.google.zxing:core:3.5.3'
implementation 'androidx.work:work-runtime-ktx:2.10.0'
implementation 'androidx.core:core-ktx:1.15.0'
```

Compose, Firebase Auth ও Firestore dependencies মূল ZIP-এ আগে থেকেই আছে।

## গুরুত্বপূর্ণ
- Student photo app-private phone storage-এ রাখা হয়; Firebase Storage-এ upload নয়।
- ID Card PDF/Image ফোনের Documents/Pictures-এ save হয়।
- Offline PDF Google Drive URL থেকে DownloadManager দিয়ে ফোনে নামে। Drive link public/direct-download compatible হতে হবে।
- NotificationScreen হলো Firestore in-app inbox; এটি নিজে remote push পাঠায় না।
- LocalReminderWorker ফোনে scheduled reminder দেখায়। Android 13+ এ POST_NOTIFICATIONS runtime permission Activity থেকে request করতে হবে।
- Smart remote push-এর জন্য trusted sender প্রয়োজন; FCM server credential client app-এ রাখা যাবে না।
- Firestore collections/fields এবং security rules README-র বাইরে মূল project-এ configure করতে হবে।
- এই নতুন files standalone source; MainActivity integration, dependency update, manifest permission, Firestore rules, build/test এখনো বাকি।
