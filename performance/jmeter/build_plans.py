"""Generate stock Apache JMeter 5.6.3 plans (no plugins)."""
from pathlib import Path
import xml.etree.ElementTree as E
ROOT=Path(__file__).resolve().parent
def prop(node,name,value,kind="stringProp"):
    E.SubElement(node,kind,{"name":name}).text=str(value).lower() if isinstance(value,bool) else str(value)
def element(tag,name,gui=None):
    return E.Element(tag,dict(guiclass=gui or tag,testclass=tag,testname=name,enabled="true"))
def add(tree,node):
    tree.append(node)
    return E.SubElement(tree,"hashTree")
def script(tree,tag,name,file):
    node=element(tag,name,"TestBeanGUI")
    for k,v in {"scriptLanguage":"groovy","parameters":"","filename":"${scripts_dir}/"+file,"cacheKey":"true","script":""}.items():prop(node,k,v)
    return add(tree,node)
def http(tree,label,path,method="GET",body=None,query=None):
    node=element("HTTPSamplerProxy",label,"HttpTestSampleGui")
    args=E.SubElement(node,"elementProp",{"name":"HTTPsampler.Arguments","elementType":"Arguments","guiclass":"HTTPArgumentsPanel","testclass":"Arguments","enabled":"true"})
    collection=E.SubElement(args,"collectionProp",{"name":"Arguments.arguments"})
    if body is not None:
        value=E.SubElement(collection,"elementProp",{"name":"","elementType":"HTTPArgument"})
        prop(value,"HTTPArgument.always_encode",False,"boolProp");prop(value,"Argument.value",body);prop(value,"Argument.metadata","=")
        prop(node,"HTTPSampler.postBodyRaw",True,"boolProp")
    else:
        for key,text in (query or {}).items():
            value=E.SubElement(collection,"elementProp",{"name":key,"elementType":"HTTPArgument"})
            prop(value,"HTTPArgument.always_encode",True,"boolProp");prop(value,"Argument.name",key)
            prop(value,"Argument.value",text);prop(value,"Argument.metadata","=");prop(value,"HTTPArgument.use_equals",True,"boolProp")
    for k,v in {"HTTPSampler.path":"${__P(path_prefix,)}"+path,"HTTPSampler.method":method,"HTTPSampler.contentEncoding":"UTF-8","HTTPSampler.connect_timeout":"${__P(connect_ms,3000)}","HTTPSampler.response_timeout":"${__P(response_ms,15000)}"}.items():prop(node,k,v)
    for k,v in {"HTTPSampler.follow_redirects":False,"HTTPSampler.auto_redirects":False,"HTTPSampler.use_keepalive":True,"HTTPSampler.DO_MULTIPART_POST":False,"HTTPSampler.image_parser":False}.items():prop(node,k,v,"boolProp")
    return add(tree,node)
def base(name,scenario,csv="users.csv",recycle=False,threads=10,ramp=5,duration=False):
    doc=E.Element("jmeterTestPlan",{"version":"1.2","properties":"5.0","jmeter":"5.6.3"});top=E.SubElement(doc,"hashTree")
    plan=element("TestPlan",name,"TestPlanGui")
    prop(plan,"TestPlan.comments","Dedicated test data only. HTTP 200 alone is not a business success.")
    prop(plan,"TestPlan.functional_mode",False,"boolProp");prop(plan,"TestPlan.serialize_threadgroups",False,"boolProp")
    args=E.SubElement(plan,"elementProp",{"name":"TestPlan.user_defined_variables","elementType":"Arguments","guiclass":"ArgumentsPanel","testclass":"Arguments"})
    coll=E.SubElement(args,"collectionProp",{"name":"Arguments.arguments"})
    for key,value in {"data_dir":"${__P(data_dir,performance/jmeter/.runtime/data)}","scripts_dir":"${__P(scripts_dir,performance/jmeter/scripts)}","token":"","order_id":"","outcome":"","business_ms":"","reservation_id":""}.items():
        item=E.SubElement(coll,"elementProp",{"name":key,"elementType":"Argument"});prop(item,"Argument.name",key);prop(item,"Argument.value",value);prop(item,"Argument.metadata","=")
    pt=add(top,plan)
    defaults=element("ConfigTestElement","HTTP Defaults","HttpDefaultsGui")
    args=E.SubElement(defaults,"elementProp",{"name":"HTTPsampler.Arguments","elementType":"Arguments","guiclass":"HTTPArgumentsPanel","testclass":"Arguments","enabled":"true"})
    E.SubElement(args,"collectionProp",{"name":"Arguments.arguments"})
    for key,value in {"HTTPSampler.domain":"${__P(host,127.0.0.1)}","HTTPSampler.port":"${__P(port,18081)}","HTTPSampler.protocol":"${__P(protocol,http)}","HTTPSampler.implementation":"HttpClient4","HTTPSampler.contentEncoding":"UTF-8"}.items():prop(defaults,key,value)
    add(pt,defaults)
    headers=element("HeaderManager","JSON and per-user authorization","HeaderPanel");coll=E.SubElement(headers,"collectionProp",{"name":"HeaderManager.headers"})
    for key,value in {"Content-Type":"application/json","Accept":"application/json","authorization":"${token}"}.items():
        item=E.SubElement(coll,"elementProp",{"name":"","elementType":"Header"});prop(item,"Header.name",key);prop(item,"Header.value",value)
    add(pt,headers)
    group=element("ThreadGroup",scenario,"ThreadGroupGui");prop(group,"ThreadGroup.on_sample_error","continue")
    loop=E.SubElement(group,"elementProp",{"name":"ThreadGroup.main_controller","elementType":"LoopController","guiclass":"LoopControlPanel","testclass":"LoopController","testname":"One user per thread"})
    prop(loop,"LoopController.continue_forever",False,"boolProp");prop(loop,"LoopController.loops",1)
    prop(group,"ThreadGroup.num_threads","${__P(threads,"+str(threads)+")}")
    prop(group,"ThreadGroup.ramp_time","${__P(ramp_s,"+str(ramp)+")}")
    prop(group,"ThreadGroup.scheduler",duration,"boolProp");prop(group,"ThreadGroup.duration","${__P(duration_s,60)}")
    prop(group,"ThreadGroup.delay","0");prop(group,"ThreadGroup.same_user_on_next_iteration",True,"boolProp")
    gt=add(pt,group)
    data=element("CSVDataSet","CSV: "+csv,"TestBeanGUI")
    for key,value in {"filename":"${data_dir}/"+csv,"fileEncoding":"UTF-8","variableNames":"user_id,phone,code,shop_id,type_id,x,y,voucher_id,reservation_time","delimiter":",","shareMode":"shareMode.all"}.items():prop(data,key,value)
    for key,value in {"ignoreFirstLine":True,"quotedData":False,"recycle":recycle,"stopThread":True}.items():prop(data,key,value,"boolProp")
    add(gt,data)
    return doc,gt
def login(tree,label="auth.login"):
    child=http(tree,label,"/user/login","POST",'{"phone":"${phone}","code":"${code}"}')
    script(child,"JSR223PostProcessor","Extract token; stop unauthenticated user","login.groovy")
def loop(tree,name,count):
    node=element("LoopController",name,"LoopControlPanel");prop(node,"LoopController.continue_forever",False,"boolProp");prop(node,"LoopController.loops",count);return add(tree,node)
def timer(tree,name,ms):
    node=element("ConstantTimer",name,"ConstantTimerGui");prop(node,"ConstantTimer.delay",ms);add(tree,node)
def iff(tree,name,condition):
    node=element("IfController",name,"IfControllerPanel");prop(node,"IfController.condition",condition);prop(node,"IfController.evaluateAll",False,"boolProp");prop(node,"IfController.useExpression",True,"boolProp");return add(tree,node)
def write(doc,file):
    E.indent(doc,space="  ");E.ElementTree(doc).write(ROOT/file,encoding="UTF-8",xml_declaration=True)
def generate():
    doc,tree=base("HMDP - mixed read load","Read load",duration=True)
    login(tree);work=loop(tree,"Measured read loops","${__P(loops,-1)}");timer(work,"Think time per read","${__P(think_ms,100)}")
    reads=[
        ("read.shop_detail","/shop/${shop_id}",{}),
        ("read.shop_type","/shop/of/type",{"typeId":"${type_id}","current":"1"}),
        ("read.nearby","/shop/of/type",{"typeId":"${type_id}","current":"1","x":"${x}","y":"${y}"}),
        ("read.blog_hot","/blog/hot",{"current":"1"}),
        ("read.shop_blogs","/blog/of/shop",{"shopId":"${shop_id}","current":"1"}),
        ("read.vouchers","/voucher/list/${shop_id}",{}),
        ("read.my_orders","/voucher-order/my",{"current":"1"}),
        ("read.my_reservations","/reservation/my",{"current":"1"}),
        ("read.skills_catalog","/ai/skills",{})]
    for label,path,query in reads:script(http(work,label,path,query=query),"JSR223Assertion","HTTP and business success","assert_success.groovy")
    write(doc,"read-load.jmx")
    doc,tree=base("HMDP - login load","Login load");work=loop(tree,"Login loops","${__P(loops,1)}");login(work,"auth.login_measured");write(doc,"login-load.jmx")
    for file,title,csv,recycle in [("seckill-load.jmx","Unique user oversell test","users.csv",False),("duplicate-load.jmx","Same user duplicate test","same-user.csv",True),("claim-load.jmx","Ordinary voucher claim","claim-users.csv",False)]:
        doc,tree=base("HMDP - "+title,title,csv,recycle,threads=20,ramp=2);login(tree);work=loop(tree,"Attempts per user","${__P(attempts,1)}")
        claim=file=="claim-load.jmx"
        child=http(work,"claim.submit" if claim else "seckill.submit","/voucher-order/"+("claim" if claim else "seckill")+"/${voucher_id}","POST")
        sync=element("SyncTimer","Burst: all threads, max 30s wait","TestBeanGUI");prop(sync,"groupSize",0,"intProp");prop(sync,"timeoutInMs",30000,"longProp");add(child,sync)
        script(child,"JSR223PreProcessor","Start business stopwatch","start.groovy");script(child,"JSR223PostProcessor","Classify business result","submit.groovy")
        accepted=iff(work,"Accepted orders only","${__groovy(vars.get('outcome') == 'ACCEPTED')}")
        polling=element("WhileController","Poll until terminal state or timeout","WhileControllerGui")
        prop(polling,"WhileController.condition","${__groovy(vars.get('poll_status') == 'PENDING' && Integer.parseInt(vars.get('poll_count')) < Integer.parseInt(props.getProperty('poll_limit'\\,'100')))}")
        poll=http(add(accepted,polling),"seckill.poll","/voucher-order/result/${order_id}");timer(poll,"Poll interval","${__P(poll_ms,200)}")
        script(poll,"JSR223PostProcessor","Read final state","poll.groovy");script(accepted,"JSR223Sampler","order.final","final.groovy");write(doc,file)
    doc,tree=base("HMDP - reservation lifecycle","Reservation create and cancel");login(tree);work=loop(tree,"Reservation loops","${__P(loops,1)}")
    child=http(work,"reservation.create","/reservation","POST",'{"shopId":${shop_id},"reservationTime":"${reservation_time}","numPeople":2,"remark":"JMeter isolated test"}')
    script(child,"JSR223PostProcessor","Capture reservation id","reservation.groovy")
    created=iff(work,"Cancel test reservation","${__groovy(vars.get('reservation_id') != '')}")
    script(http(created,"reservation.cancel","/reservation/${reservation_id}/cancel","PUT"),"JSR223Assertion","Cancellation business success","assert_success.groovy")
    write(doc,"reservation-load.jmx")
if __name__=="__main__":
    generate();print("Generated 6 JMeter plans")
