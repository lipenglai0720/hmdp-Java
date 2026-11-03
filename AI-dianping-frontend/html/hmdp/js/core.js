/* Shared client. Session credentials are read for every request. */
(function () {
"use strict";
const App=window.App={version:"20261029",pages:{},query:new URLSearchParams(location.search),
toast(message,kind){window.dispatchEvent(new CustomEvent("toast",{detail:{message:String(message),kind:kind||"info"}}));},
login(){location.href="/login.html?redirect="+encodeURIComponent(location.pathname+location.search);},
signedIn(){if(sessionStorage.getItem("token"))return true;App.login();return false;},
async api(path,options){
 options=options||{};const query=new URLSearchParams();
 Object.entries(options.query||{}).forEach(([k,v])=>{if(v!==undefined&&v!==null&&v!=="")query.set(k,String(v));});
 const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),options.timeout||15000);
 const headers={Accept:"application/json"},token=sessionStorage.getItem("token");if(token)headers.authorization=token;
 let body=options.body;if(body&&!(body instanceof FormData)){headers["Content-Type"]="application/json";body=JSON.stringify(body);}
 try{
  const response=await fetch("/api"+path+(query.size?"?"+query:""),{method:options.method||"GET",headers,body,signal:controller.signal});
  if(response.status===401){sessionStorage.removeItem("token");window.dispatchEvent(new Event("session-expired"));const error=new Error("登录已过期，请重新登录");error.status=401;throw error;}
  if(!response.ok)throw new Error(response.status===403?"没有操作权限":"服务暂时不可用，请稍后重试");
  const result=await response.json();if(result.success===false)throw new Error(result.errorMsg||"操作未完成");return result;
 }catch(error){if(error.name==="AbortError")throw new Error("请求超时，请稍后重试");if(error instanceof TypeError)throw new Error("网络连接失败，请检查服务是否启动");throw error;}finally{clearTimeout(timer);}
},
image(value,fallback){const s=String(value||"").split(",")[0].trim();if(/^https?:\/\//i.test(s)||s.startsWith("/imgs/")||s.startsWith("/assets/"))return s;if(s.startsWith("/blogs/")||s.startsWith("/shop/"))return "/imgs"+s;return fallback||"/assets/images/food-story.png";},
imageError(event){const fallback="/assets/images/city-guide.png";if(!event.target.src.endsWith(fallback))event.target.src=fallback;},
money(value){return (Number(value||0)/100).toFixed(2).replace(/\.00$/,"");},
date(value){if(!value)return "—";const date=new Date(value);return isNaN(date)?String(value):date.toLocaleString("zh-CN",{month:"2-digit",day:"2-digit",hour:"2-digit",minute:"2-digit",hour12:false});},
plain(value){return new DOMParser().parseFromString(String(value||""),"text/html").body.textContent||"";},
pendingKey(){return "pending-orders:"+((window.app&&window.app.me&&window.app.me.id)||"");},
rememberOrder(id){const key=App.pendingKey();let ids=JSON.parse(sessionStorage.getItem(key)||"[]");if(!ids.includes(id))ids.push(id);sessionStorage.setItem(key,JSON.stringify(ids.slice(-20)));},
forgetOrder(id){const key=App.pendingKey();sessionStorage.setItem(key,JSON.stringify(JSON.parse(sessionStorage.getItem(key)||"[]").filter(x=>x!==id)));}
};
const paths={
compass:["M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Z","m16 8-3 5-5 3 3-5 5-3Z"],
search:["m21 21-4.5-4.5","M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16Z"],
arrow:["M4 12h16","m14 6 6 6-6 6"],
location:["M19 10c0 6-7 11-7 11S5 16 5 10a7 7 0 0 1 14 0Z","M12 7a3 3 0 1 0 0 6 3 3 0 0 0 0-6Z"],
note:["M6 3h9l4 4v14H6Z","M14 3v5h5","M9 12h7M9 16h5"],
heart:["M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z"],
spark:["m12 2 2.5 7.5L22 12l-7.5 2.5L12 22l-2.5-7.5L2 12l7.5-2.5L12 2Z"],
user:["M12 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z","M4 21v-2a8 8 0 0 1 16 0v2"],
calendar:["M4 5h16v16H4Z","M8 2v6M16 2v6M4 10h16","m9 15 2 2 4-4"],
ticket:["M3 7h18v4a2 2 0 0 0 0 4v4H3v-4a2 2 0 0 0 0-4Z","M15 7v3m0 2v2m0 2v3"],
store:["M3 10h18l-2-7H5Z","M5 10v11h14V10M9 21v-7h6v7"],
clock:["M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Z","M12 7v5l3 2"],
close:["m6 6 12 12M18 6 6 18"],plus:["M12 4v16M4 12h16"],check:["m5 12 4 4L19 6"],
message:["M21 15a3 3 0 0 1-3 3H8l-5 4V5a3 3 0 0 1 3-3h12a3 3 0 0 1 3 3Z","M7 8h10M7 12h6"],
star:["m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-2.9-5.6 2.9L7.5 14 3 9.6l6.2-.9Z"]};
Vue.component("i-icon",{props:["name"],computed:{paths(){return paths[this.name]||paths.compass;}},template:'<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path v-for="(d,i) in paths" :key="i" :d="d"></path></svg>'});
Vue.component("state-box",{props:["loading","error","empty","label"],template:'<div v-if="loading || error || empty" class="state-box" :role="error ? \'alert\' : \'status\'"><span v-if="loading" class="spinner"></span><i-icon v-else :name="error ? \'message\' : \'compass\'"></i-icon><h3>{{loading ? "正在加载" : error ? "暂时无法加载" : label || "这里还没有内容"}}</h3><p v-if="error">{{error}}</p><button v-if="error" class="btn subtle" @click="$emit(\'retry\')">重新加载</button></div>'});
App.mixin={data(){return {loading:false,error:"",busy:false};},computed:{user(){return this.$root.me;},types(){return this.$root.types;}},methods:{
image:App.image,imageError:App.imageError,money:App.money,date:App.date,plain:App.plain,
async attempt(action){try{return await action();}catch(e){App.toast(e.message,"error");return null;}},
requireLogin:App.signedIn,async load(action){this.loading=true;this.error="";try{await action();}catch(e){this.error=e.message;}finally{this.loading=false;}}
}};
Vue.component("shop-card",{props:["shop"],mixins:[App.mixin],template:'<a class="shop-card" :href="\'/shop-detail.html?id=\'+shop.id"><div class="card-image"><img :src="image(shop.images)" :alt="shop.name" loading="lazy" @error="imageError"><span class="image-label"><i-icon name="location"></i-icon>{{shop.area || "城市好店"}}</span></div><div class="card-copy"><div class="card-title"><h3>{{shop.name}}</h3><span class="rating" v-if="shop.score"><i-icon name="star"></i-icon>{{(shop.score/10).toFixed(1)}}</span></div><p class="muted clamp">{{shop.address || "到店地址待补充"}}</p><div class="card-bottom"><span><b>¥{{shop.avgPrice || "—"}}</b> / 人</span><span v-if="shop.distance!=null">{{shop.distance>=1000?(shop.distance/1000).toFixed(1)+" km":Math.round(shop.distance)+" m"}}</span><span v-else class="link-text">去看看 <i-icon name="arrow"></i-icon></span></div></div></a>'});
Vue.component("note-card",{props:["blog"],mixins:[App.mixin],template:'<a class="note-card" :href="\'/blog-detail.html?id=\'+blog.id"><div class="card-image"><img :src="image(blog.images)" :alt="blog.title" loading="lazy" @error="imageError"><span class="image-label">探店笔记</span></div><div class="card-copy"><h3 class="clamp">{{blog.title}}</h3><p class="muted clamp">{{plain(blog.content)}}</p><div class="note-author"><span><img :src="image(blog.icon,\'/assets/avatar.svg\')" alt="" @error="imageError">{{blog.name || "探店用户"}}</span><span><i-icon name="heart"></i-icon>{{blog.liked || 0}}</span></div></div></a>'});
})();
