(async function(){
"use strict";
const routes={"index.html":["home","发现好店"],"shop-list.html":["shops","城市好店"],"shop-detail.html":["shop","店铺详情"],"notes.html":["notes","探店笔记"],"blog-detail.html":["blog","探店笔记"],"blog-edit.html":["editor","发布笔记"],"skills.html":["skills","AI 工作台"],"aiassistant.html":["chat","AI 对话助手"],"reservation.html":["reserve","预约到店"],"reservations.html":["reservations","预约管理"],"orders.html":["orders","我的优惠订单"],"merchant.html":["merchant","商家工作台"],"info.html":["profile","个人中心"],"other-info.html":["profile","探店作者"],"info-edit.html":["account","账户资料"],"login.html":["login","登录"],"login2.html":["login","登录"],"about.html":["about","平台说明"]};
const file=location.pathname.split("/").pop()||"index.html",route=routes[file]||routes["index.html"],page=route[0];document.title=route[1]+" · 探见";
try{
 const responses=await Promise.all([fetch("/views/shell.html?v="+App.version),fetch("/views/"+page+".html?v="+App.version)]);
 if(responses.some(r=>!r.ok))throw new Error("页面加载失败，请刷新重试");
 const [shell,template]=await Promise.all(responses.map(r=>r.text())),definition=App.pages[page];if(!definition)throw new Error("页面未正确加载");definition.template=template;
 window.app=new Vue({el:"#app",template:shell,components:{"page-content":definition},
 data:{page,title:route[1],me:null,sessionLoading:!!sessionStorage.getItem("token"),types:[],toast:null,toastTimer:null},
 computed:{
 active(){if(["shops","shop","home"].includes(this.page))return "home";if(["notes","blog","editor"].includes(this.page))return "notes";if(["skills","chat"].includes(this.page))return "skills";return "profile";},
 requiresAuth(){return ["editor","reserve","reservations","orders","merchant","account","chat"].includes(this.page)||(this.page==="profile"&&!App.query.get("id"));},
 canRender(){return !this.requiresAuth||!!this.me;},
 workspace(){return ["skills","chat","reservations","orders","merchant","profile","account"].includes(this.page);}
 },
 async created(){
 window.addEventListener("toast",this.receiveToast);window.addEventListener("session-expired",this.expired);
 App.api("/shop-type/list").then(r=>{this.types=r.data||[];}).catch(()=>{});
 if(sessionStorage.getItem("token"))try{const r=await App.api("/user/me");this.me=r.data||null;}catch(e){this.me=null;}finally{this.sessionLoading=false;}
 },
 beforeDestroy(){window.removeEventListener("toast",this.receiveToast);window.removeEventListener("session-expired",this.expired);clearTimeout(this.toastTimer);},
 methods:{image:App.image,login:App.login,receiveToast(event){clearTimeout(this.toastTimer);this.toast=event.detail;this.toastTimer=setTimeout(()=>{this.toast=null;},4500);},expired(){this.me=null;this.sessionLoading=false;},async logout(){try{await App.api("/user/logout",{method:"POST"});}catch(e){}sessionStorage.removeItem("token");this.me=null;location.href="/index.html";}}
 });
}catch(e){const root=document.getElementById("app");root.textContent="";const box=document.createElement("div");box.className="boot";box.textContent=e.message;const button=document.createElement("button");button.className="btn";button.textContent="刷新页面";button.onclick=()=>location.reload();box.appendChild(button);root.appendChild(box);}
})();
