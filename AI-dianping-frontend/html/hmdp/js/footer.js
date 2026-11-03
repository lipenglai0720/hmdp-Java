Vue.component("footBar", {
  props: ["activeBtn"],
  template: '<nav class="foot" aria-label="移动端导航"><a href="/" class="foot-box" :class="{active:activeBtn===1}"><ui-icon name="compass"></ui-icon><span>发现</span></a><a href="/shop-list.html?type=1&amp;name=美食" class="foot-box" :class="{active:activeBtn===2}"><ui-icon name="pin"></ui-icon><span>好店</span></a><a href="/blog-edit.html" class="foot-box publish"><span class="publish-mark"><ui-icon name="plus"></ui-icon></span><span>发笔记</span></a><a href="/skills.html" class="foot-box" :class="{active:activeBtn===3}"><ui-icon name="sparkle"></ui-icon><span>AI 灵感</span></a><a href="/info.html" class="foot-box" :class="{active:activeBtn===4}"><ui-icon name="user"></ui-icon><span>我的</span></a></nav>'
});
