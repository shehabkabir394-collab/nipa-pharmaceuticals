const CACHE_NAME='nipa-shell-v1';
self.addEventListener('install',event=>{self.skipWaiting();});
self.addEventListener('activate',event=>{event.waitUntil(self.clients.claim());});
self.addEventListener('push',event=>{
  let data={title:'Nipa MPO Order',body:'আপনার নতুন message এসেছে।',url:'./'};
  try{if(event.data)data={...data,...event.data.json()};}catch(e){if(event.data)data.body=event.data.text();}
  event.waitUntil(self.registration.showNotification(data.title||'Nipa MPO Order',{
    body:data.body||'আপনার নতুন message এসেছে।',
    icon:'./icon.svg',badge:'./icon.svg',tag:data.tag||'nipa-message',
    data:{url:data.url||'./'},renotify:true
  }));
});
self.addEventListener('notificationclick',event=>{
  event.notification.close();
  const target=new URL(event.notification.data?.url||'./',self.registration.scope).href;
  event.waitUntil(self.clients.matchAll({type:'window',includeUncontrolled:true}).then(list=>{
    for(const client of list){if(client.url===target&&'focus'in client)return client.focus();}
    if(self.clients.openWindow)return self.clients.openWindow(target);
  }));
});
