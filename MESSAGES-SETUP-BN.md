# MPO / Admin message setup

HTML-এ inbox, নতুন message, conversation view এবং unread badge যোগ হয়েছে। Message Supabase-এ থাকবে; active MPO ও Admin account-কে বেছে নেওয়া যাবে। Inbox badge চালু থাকা অ্যাপ খোলা অবস্থায় প্রায় ৩০ সেকেন্ড পরপর refresh হয়।

এটি চালু করতে Supabase Dashboard → **SQL Editor**-এ `messages_setup.sql`-এর SQL একবার চালান। তারপর `E:\Nipa\index\index.html` refresh করে login করুন। Message table-এ Row Level Security আছে: user কেবল নিজের পাঠানো বা পাওয়া message পড়তে পারবে; পাঠানো ও read status পরিবর্তন নির্দিষ্ট authenticated RPC function দিয়ে হয়।

অ্যাপ বন্ধ থাকলে push notification যাবে না; নতুন/unread message দেখতে অ্যাপ খুলে রাখতে হবে।
