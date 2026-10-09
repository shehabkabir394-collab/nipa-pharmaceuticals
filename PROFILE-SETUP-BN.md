# প্রোফাইল চালু করা

`index.html`-এ ছবির মতো Profile screen যোগ হয়েছে। ছবি এই browser/device-এ account অনুযায়ী সংরক্ষিত হয়; ভাষা ও theme-ও account অনুযায়ী এই device-এ মনে রাখা হয়। নাম ও MPO Code Supabase-এর `profiles.full_name` ও `profiles.mpo_code`-এ সংরক্ষিত হবে।

নাম বা MPO Code নিজের account-এ নিরাপদে বদলাতে Supabase Dashboard → **SQL Editor**-এ `profile_update_rpc.sql`-এর পুরো SQL একবার চালান। Function শুধু signed-in user-এর নিজের `full_name` ও `mpo_code` বদলাতে দেয়; role, active status, বা email বদলাতে দেয় না।

তারপর অ্যাপ refresh করে Profile খুলুন। যদি SQL function না চালানো হয়, profile edit-এ error দেখাবে; অন্য profile options তখনও কাজ করবে।
