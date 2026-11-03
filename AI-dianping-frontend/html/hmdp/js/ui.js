(function () {
  "use strict";
  var covers = ["/assets/images/food-story.png", "/assets/images/coffee-story.png", "/assets/images/city-guide.png"];
  var paths = {
    pin: "M20 10c0 6-8 11-8 11S4 16 4 10a8 8 0 1 1 16 0ZM15 10a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z",
    search: "M21 21l-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z",
    arrow: "M4 12h16M14 6l6 6-6 6", back: "M20 12H4m6-6-6 6 6 6",
    down: "m6 9 6 6 6-6", plus: "M12 5v14M5 12h14", close: "m6 6 12 12M6 18 18 6",
    home: "m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1Z",
    user: "M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0ZM4 21v-2a8 8 0 0 1 16 0v2",
    compass: "M22 12A10 10 0 1 1 2 12a10 10 0 0 1 20 0Zm-6-4-3 6-5 2 2-5Z",
    sparkle: "m12 2 2.6 7.4L22 12l-7.4 2.6L12 22l-2.6-7.4L2 12l7.4-2.6ZM20 2v4m-2-2h4",
    heart: "M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z",
    star: "m12 2 3 6 7 .9-5 4.9 1.2 7L12 17.5 5.8 21 7 13.8 2 8.9 9 8Z",
    food: "M4 3v6m4-6v6M2 6h8M6 9v12M18 3c-4 4-4 8 0 8h2V3h-2Zm2 8v10",
    music: "M10 18V5l10-2v13M10 5v4l10-2M10 18a3 3 0 1 1-3-3c1.7 0 3 1.3 3 3Zm10-2a3 3 0 1 1-3-3c1.7 0 3 1.3 3 3Z",
    scissors: "M9 7A3 3 0 1 1 3 7a3 3 0 0 1 6 0ZM9 17a3 3 0 1 1-6 0 3 3 0 0 1 6 0ZM8.5 8.5 21 20M8.5 15.5 21 4",
    fitness: "m3 8 5-5m8 18 5-5M2 6l4-4 4 4-4 4Zm12 12 4-4 4 4-4 4ZM8 8l8 8",
    spa: "M12 21c-7-1-10-5-9-11 4 0 7 2 9 6m0 5c7-1 10-5 9-11-4 0-7 2-9 6m0 5C7 16 7 9 12 3c5 6 5 13 0 18Z",
    smile: "M22 12A10 10 0 1 1 2 12a10 10 0 0 1 20 0ZM8 8h.01M16 8h.01M7 14c2 5 8 5 10 0",
    wine: "M8 2h8l2 6c1 4-2 6-6 6s-7-2-6-6ZM12 14v8m-4 0h8M7 8h10",
    house: "m3 10 9-7 9 7M5 9v12h14V9M10 21v-7h4v7",
    flower: "M12 8c-5-8 5-8 0 0Zm0 0c8-5 8 5 0 0Zm0 0c5 8-5 8 0 0Zm0 0c-8 5-8-5 0 0Zm0 5v9m0-3c2-5 6-4 6-4s0 5-6 4Zm0 1c-2-5-6-4-6-4s0 5 6 4Z",
    coffee: "M4 3h12v11c0 6-12 6-12 0Zm12 2h2a4 4 0 0 1 0 8h-2M2 22h18",
    edit: "m16 3 5 5L9 20l-6 1 1-6ZM14 5l5 5", clock: "M22 12A10 10 0 1 1 2 12a10 10 0 0 1 20 0ZM12 6v6l4 2",
    copy: "M9 9h12v12H9ZM15 5V2H2v13h3", refresh: "M20 7A9 9 0 1 0 21 15M20 2v6h-6",
    check: "m5 12 4 4L19 6",
    shield: "m12 2 8 3v6c0 6-8 11-8 11S4 17 4 11V5ZM8 11l3 3 5-5"
  };
  window.CityUI = {
    covers: covers,
    cover: function (value, index) { var source = Array.isArray(value) ? value[0] : String(value || "").split(",")[0]; return source || covers[(Number(index) || 0) % covers.length]; },
    categoryIcon: function (id) { return ["food", "music", "scissors", "fitness", "spa", "spa", "smile", "wine", "house", "flower"][(Number(id) - 1) % 10] || "compass"; },
    rating: function (score) { return score ? (Number(score) / 10).toFixed(1) : "暂无评分"; },
    error: function (error) { return typeof error === "string" ? error : error && error.message || "加载失败，请稍后重试"; },
    login: function () { location.href = "/login.html?redirect=" + encodeURIComponent(location.pathname + location.search); }
  };
  document.addEventListener("error", function (event) {
    var image = event.target;
    if (!image || image.tagName !== "IMG" || image.dataset.fallbackDone) return;
    image.dataset.fallbackDone = "true";
    var source = image.getAttribute("src") || "";
    var avatar = /icons\/|avatar|headpic|thumbup|add\.png/.test(source) || image.closest(".basic-icon,.blog-user-icon,.comment-icon,.user-icon-mini");
    var hash = source.split("").reduce(function (sum, char) { return sum + char.charCodeAt(0); }, 0);
    image.src = avatar ? "/assets/avatar.svg" : covers[hash % covers.length];
    image.alt = image.alt || (avatar ? "用户头像" : "城市生活氛围配图");
    var frame = image.closest(".card-media,.photo-frame,.note-cover");
    if (frame && !avatar) frame.classList.add("is-fallback");
  }, true);
  Vue.component("ui-icon", { props: ["name"], computed: { path: function () { return paths[this.name] || paths.sparkle; } }, template: '<svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path :d="path"></path></svg>' });
  Vue.component("site-header", {
    props: ["active"],
    data: function () { return { keyword: new URLSearchParams(location.search).get("q") || "", signedIn: !!sessionStorage.getItem("token") }; },
    methods: {
      search: function () { if (this.keyword.trim()) location.href = "/shop-list.html?q=" + encodeURIComponent(this.keyword.trim()); },
      city: function () { this.$message({ message: "当前为杭州城市指南，可按分类或店名探索本地商户。", type: "info" }); }
    },
    template: '<header class="site-header"><div class="site-header-inner"><a class="brand" href="/" aria-label="黑马点评首页"><span class="brand-mark"><ui-icon name="pin"></ui-icon></span><span class="brand-copy"><strong>黑马点评</strong><small>GOOD DAYS, NEARBY</small></span></a><button class="city-picker" @click="city" aria-label="当前城市杭州"><ui-icon name="pin"></ui-icon>杭州<ui-icon name="down"></ui-icon></button><nav class="header-nav" aria-label="主导航"><a href="/" :class="{\'is-active\':active===\'discover\'}">发现好生活</a><a href="/shop-list.html?type=1&amp;name=美食" :class="{\'is-active\':active===\'shops\'}">逛逛好店</a><a href="/skills.html" :class="{\'is-active\':active===\'skills\'}">AI 灵感助手</a></nav><form class="header-search-form" @submit.prevent="search" role="search"><ui-icon name="search"></ui-icon><input aria-label="搜索商户" v-model="keyword" placeholder="搜索店铺、想去的地方"></form><a class="header-account" :href="signedIn ? \'/info.html\' : \'/login.html\'"><ui-icon name="user"></ui-icon>{{signedIn ? "个人中心" : "登录 / 注册"}}</a><a class="header-create" href="/blog-edit.html"><ui-icon name="edit"></ui-icon>发布笔记</a></div></header>'
  });
  Vue.component("site-footer", {
    template: '<footer class="site-footer"><div class="site-footer-inner"><div><a class="brand" href="/"><span class="brand-mark"><ui-icon name="pin"></ui-icon></span><span class="brand-copy"><strong>黑马点评</strong></span></a><p>发现一间好店，记录每一个值得喜欢的日常。</p></div><nav class="footer-links" aria-label="页脚导航"><a href="/shop-list.html?type=1&amp;name=美食">探索商户</a><a href="/skills.html">AI 灵感助手</a><a href="/about.html">关于与帮助</a></nav></div></footer>'
  });
})();
