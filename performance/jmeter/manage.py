"""Isolated local JMeter fixtures and application lifecycle; Python standard library only."""
from pathlib import Path
import argparse,csv,datetime as dt,getpass,json,os,re,secrets,shutil,socket,subprocess,sys,time,urllib.request
ROOT=Path(__file__).resolve().parents[2]
BASE=Path(__file__).resolve().parent
STATE=BASE/".runtime"
CURRENT=STATE/"current.json"
NO_WINDOW=getattr(subprocess,"CREATE_NO_WINDOW",0)
MYSQL_DEFAULT=r"D:\biyeshejixuexi\e_software\mysql-8.0.34-winx64\bin\mysql.exe"
REDIS_DEFAULT=r"D:\Redis\Redis-8.10.2-Windows-x64-msys2\redis-server.exe"
JAVA_DEFAULT=r"D:\biyeshejixuexi\e_software\Java_jdk\jdk-17.0.13\bin\java.exe"
FIELDS=["user_id","phone","code","shop_id","type_id","x","y","voucher_id","reservation_time"]
PASSWORD=None

def say(value): print(value if isinstance(value,str) else json.dumps(value,ensure_ascii=False),flush=True)
def password():
    global PASSWORD
    if PASSWORD is None: PASSWORD=os.environ.get("MYSQL_PWD") or os.environ.get("HMDP_MYSQL_PASSWORD") or getpass.getpass("MySQL password: ")
    return PASSWORD
def mysql(meta,sql,database=None):
    env=os.environ.copy();env["MYSQL_PWD"]=password()
    args=[meta["mysql"],"-h127.0.0.1","-P"+str(meta.get("mysql_port",3306)),"-u"+meta["mysql_user"],"--default-character-set=utf8mb4","--batch","--skip-column-names"]
    if database: args.append(database)
    result=subprocess.run(args,input=sql.encode("utf-8"),stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=env,creationflags=NO_WINDOW)
    if result.returncode: raise RuntimeError("MySQL: "+result.stderr.decode("utf-8",errors="replace"))
    return result.stdout.decode("utf-8",errors="replace").strip()
class Redis:
    def __init__(self,port):
        self.connection=socket.create_connection(("127.0.0.1",port),timeout=5);self.file=self.connection.makefile("rb")
    def call(self,*args):
        chunks=[str(x).encode("utf-8") for x in args]
        data=b"*"+str(len(chunks)).encode()+b"\r\n"
        data+=b"".join(b"$"+str(len(x)).encode()+b"\r\n"+x+b"\r\n" for x in chunks)
        self.connection.sendall(data)
        return self.read()
    def read(self):
        raw=self.file.readline()
        if not raw: raise RuntimeError("Redis disconnected")
        kind,value=raw[:1],raw[1:-2]
        if kind==b"+": return value.decode()
        if kind==b"-": raise RuntimeError("Redis: "+value.decode(errors="replace"))
        if kind==b":": return int(value)
        if kind==b"$":
            size=int(value)
            if size<0:return None
            data=self.file.read(size);self.file.read(2);return data.decode("utf-8")
        if kind==b"*": return [self.read() for _ in range(int(value))]
        raise RuntimeError("Invalid Redis response")
    def close(self):
        self.file.close();self.connection.close()
def available(port):
    with socket.socket() as s:
        return s.connect_ex(("127.0.0.1",port))!=0
def save(meta):
    STATE.mkdir(exist_ok=True,parents=True)
    CURRENT.write_text(json.dumps(meta,ensure_ascii=False,indent=2),encoding="utf-8")
    (Path(meta["dir"])/"fixture.json").write_text(json.dumps(meta,ensure_ascii=False,indent=2),encoding="utf-8")
def state():
    if not CURRENT.exists(): raise RuntimeError("Run prepare first")
    meta=json.loads(CURRENT.read_text(encoding="utf-8"))
    if not re.fullmatch(r"hmdp_perf_[a-z0-9_]+",meta["database"]): raise RuntimeError("Unsafe test database name")
    folder=Path(meta["dir"]).resolve()
    if STATE.resolve() not in folder.parents: raise RuntimeError("Runtime path must remain inside .runtime")
    if int(meta["redis_port"])<10000 or int(meta["api_port"])<10000: raise RuntimeError("Not an isolated port")
    return meta
def start(args,cwd,log,env=None):
    with open(log,"ab") as output:
        return subprocess.Popen(args,cwd=str(cwd),env=env,stdout=output,stderr=subprocess.STDOUT,creationflags=NO_WINDOW)
def stop_pid(pid,signature):
    if not pid:return
    signature=signature.replace("'","''")
    command="$p=Get-CimInstance Win32_Process -Filter 'ProcessId="+str(int(pid))+"'; if($p){if(-not $p.CommandLine.Contains('"+signature+"')){throw 'Process identity changed; will not stop it'};Stop-Process -Id "+str(int(pid))+" -ErrorAction Stop}"
    result=subprocess.run(["powershell.exe","-NoProfile","-NonInteractive","-Command",command],capture_output=True,creationflags=NO_WINDOW)
    if result.returncode: raise RuntimeError(result.stderr.decode(errors="replace"))
def stop(meta,drop=False):
    stop_pid(meta.get("backend_pid"),meta["database"])
    stop_pid(meta.get("redis_pid"),meta["redis_signature"])
    for _ in range(30):
        if available(meta["api_port"]) and available(meta["redis_port"]):break
        time.sleep(.1)
    if not available(meta["api_port"]) or not available(meta["redis_port"]): raise RuntimeError("Test service did not stop; database preserved")
    if drop and not meta.get("database_dropped"):
        mysql(meta,"DROP DATABASE IF EXISTS "+meta["database"]+";")
        meta["database_dropped"]=True
    meta["stopped"]=True;save(meta)
    say({"stopped":"owned test services","database_dropped":meta.get("database_dropped",False)})
def prepare(args):
    if not 1<=args.users<=5000 or not 1<=args.stock<=args.users:raise RuntimeError("Require 1 <= stock <= users <= 5000")
    for port in [args.redis_port,args.api_port]:
        if port<10000 or not available(port):raise RuntimeError("Isolated port unavailable: "+str(port))
    if args.redis_port==args.api_port:raise RuntimeError("Redis and backend ports must differ")
    if CURRENT.exists():
        previous=state()
        if not previous.get("stopped"):raise RuntimeError("Previous test environment is still active; use stop first")
    stamp=dt.datetime.now().strftime("%Y%m%d_%H%M%S")+"_"+secrets.token_hex(2)
    folder=(STATE/stamp).resolve();folder.mkdir(parents=True)
    mysql_cli=args.mysql or shutil.which("mysql") or MYSQL_DEFAULT
    java=args.java or shutil.which("java") or JAVA_DEFAULT
    jar=ROOT/"AI-dianping-backend/target/AI-dianping-backend-0.0.1-SNAPSHOT.jar"
    for path in [mysql_cli,args.redis_server,java,str(jar)]:
        if not Path(path).is_file():raise RuntimeError("Required file not found: "+path)
    meta={"database":"hmdp_perf_"+stamp,"dir":str(folder),"mysql":str(mysql_cli),"mysql_user":args.mysql_user,"mysql_port":args.mysql_port,"redis_port":args.redis_port,"api_port":args.api_port,"users":args.users,"stock":args.stock,"start_user":900000000,"vouchers":{"seckill":800000001,"duplicate":800000002,"claim":800000003},"redis_signature":"hmdp-perf-"+stamp+".conf"}
    save(meta);created=False
    try:
        mysql(meta,"CREATE DATABASE "+meta["database"]+" CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;");created=True
        for name in ["db.sql","AI-dianping-backend/db/ai_skill_init.sql","AI-dianping-backend/db/reservation_init.sql","AI-dianping-backend/db/performance_upgrade.sql"]:
            sql=(ROOT/name).read_text(encoding="utf-8-sig")
            # The project supplements contain USE hmdp; remove only that known
            # directive in memory, then reject any other database switch.
            sql=re.sub(r"(?im)^[ \t]*USE[ \t]+`?hmdp`?[ \t]*;[ \t]*(?:\r?\n|$)","",sql)
            if re.search(r"(?im)^\s*(USE\s|(?:CREATE|DROP)\s+DATABASE\s)",sql):raise RuntimeError("Import changes database context: "+name)
            if re.search(r"(?i)(?<![a-z0-9_])`?hmdp`?\s*\.",sql):raise RuntimeError("Import refers to the business database: "+name)
            mysql(meta,sql,meta["database"])
        now=dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).replace(tzinfo=None)
        begin=(now-dt.timedelta(hours=1)).strftime("%Y-%m-%d %H:%M:%S")
        end=(now+dt.timedelta(days=1)).strftime("%Y-%m-%d %H:%M:%S")
        sql=[]
        for key,vid in meta["vouchers"].items():
            sql.append("INSERT INTO tb_voucher(id,shop_id,title,sub_title,rules,pay_value,actual_value,type,status) VALUES(%d,1,'JMeter %s','isolated test only','not for real consumption',100,200,%d,1);"%(vid,key,0 if key=="claim" else 1))
            if key!="claim":sql.append("INSERT INTO tb_seckill_voucher(voucher_id,stock,begin_time,end_time) VALUES(%d,%d,'%s','%s');"%(vid,args.stock,begin,end))
        shops=[line.split("\t") for line in mysql(meta,"SELECT id,type_id,COALESCE(x,120.15),COALESCE(y,30.27) FROM tb_shop ORDER BY id LIMIT 50;",meta["database"]).splitlines()]
        if not shops:raise RuntimeError("No shop fixtures imported")
        rows=[]
        for index in range(args.users):
            uid=meta["start_user"]+index;phone=str(19900000000+index);code=str(secrets.randbelow(900000)+100000)
            shop=shops[index%len(shops)]
            slot=(now+dt.timedelta(days=1,minutes=index%60)).isoformat(timespec="seconds")
            row=[str(uid),phone,code,*shop,str(meta["vouchers"]["seckill"]),slot];rows.append(row)
            sql.append("INSERT INTO tb_user(id,phone,nick_name,icon) VALUES(%d,'%s','JMeter_User_%04d','');"%(uid,phone,index))
        mysql(meta,"\n".join(sql),meta["database"])
        for name,data in [("users.csv",rows),("same-user.csv",[rows[0][:7]+[str(meta["vouchers"]["duplicate"]),rows[0][8]]]),("claim-users.csv",[r[:7]+[str(meta["vouchers"]["claim"]),r[8]] for r in rows])]:
            with (folder/name).open("w",encoding="utf-8",newline="") as f:
                writer=csv.writer(f);writer.writerow(FIELDS);writer.writerows(data)
        conf=folder/meta["redis_signature"]
        conf.write_text('bind 127.0.0.1\nprotected-mode yes\nport '+str(args.redis_port)+'\nsave ""\nappendonly no\ndir ./\ndbfilename perf.rdb\n',encoding="utf-8")
        process=start([args.redis_server,conf.name],folder,folder/"redis.log");meta["redis_pid"]=process.pid;save(meta)
        for _ in range(100):
            try:
                redis=Redis(args.redis_port)
                if redis.call("PING")=="PONG":break
            except (OSError,RuntimeError):
                if process.poll() is not None:raise RuntimeError("Test Redis exited; inspect redis.log")
                time.sleep(.1)
        else:raise RuntimeError("Test Redis startup timeout")
        try:
            if redis.call("DBSIZE")!=0:raise RuntimeError("Test Redis must start empty")
            for row in rows:redis.call("SET","login:code:"+row[1],row[2],"EX",7200)
        finally:redis.close()
        env=os.environ.copy();env.pop("MYSQL_PWD",None)
        env["SPRING_DATASOURCE_PASSWORD"]=password();env["SPRING_DATASOURCE_USERNAME"]=args.mysql_user
        command=[java,"-Xms128m","-Xmx512m","-jar",str(jar),"--server.address=127.0.0.1","--server.port="+str(args.api_port),
                 "--spring.datasource.url=jdbc:mysql://127.0.0.1:"+str(args.mysql_port)+"/"+meta["database"]+"?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8&allowPublicKeyRetrieval=true",
                 "--spring.data.redis.host=127.0.0.1","--spring.data.redis.port="+str(args.redis_port),"--spring.data.redis.database=0","--spring.data.redis.password=",
                 "--logging.level.com.hmdp=WARN","--spring.main.banner-mode=off","--hm.upload.path="+str(folder/"upload")]
        for model in ["chat-model","streaming-chat-model"]:
            command.extend(["--langchain4j.open-ai."+model+".base-url=http://127.0.0.1:1/v1","--langchain4j.open-ai."+model+".api-key=local-perf-disabled","--langchain4j.open-ai."+model+".log-requests=false","--langchain4j.open-ai."+model+".log-responses=false"])
        process=start(command,ROOT/"AI-dianping-backend",folder/"backend.log",env);meta["backend_pid"]=process.pid;save(meta)
        for _ in range(180):
            if process.poll() is not None:raise RuntimeError("Test backend exited; inspect backend.log")
            try:
                with urllib.request.urlopen("http://127.0.0.1:"+str(args.api_port)+"/shop-type/list",timeout=2) as response:
                    if json.load(response).get("success"):break
            except Exception:time.sleep(.5)
        else:raise RuntimeError("Test backend startup timeout")
        properties="\n".join([
            "host=127.0.0.1","port="+str(args.api_port),"protocol=http","path_prefix=",
            "data_dir="+folder.as_posix(),"scripts_dir="+(BASE/"scripts").as_posix(),
            "sample_variables=user_id,voucher_id,outcome,order_id,business_ms,reservation_id",
            "jmeter.save.saveservice.output_format=csv","jmeter.save.saveservice.print_field_names=true",
            "jmeter.save.saveservice.response_data=false","jmeter.save.saveservice.samplerData=false",
            "jmeter.save.saveservice.requestHeaders=false","jmeter.save.saveservice.responseHeaders=false",
            "jmeter.save.saveservice.assertion_results_failure_message=true","jmeter.save.saveservice.timestamp_format=ms",
            "jmeterengine.nongui.port=0","jmeter.reportgenerator.overall_granularity=1000"])+"\n"
        (STATE/"current.properties").write_text(properties,encoding="ascii")
        save(meta)
        say({"ready":True,"database":meta["database"],"api":"http://127.0.0.1:"+str(args.api_port),"redis_port":args.redis_port,"users":args.users,"stock_per_seckill":args.stock,"properties":str(STATE/"current.properties"),"credentials":"synthetic OTP codes valid for 2 hours; generated CSV files are ignored by Git"})
    except Exception:
        if created:
            try:stop(meta,True)
            except Exception as cleanup:say("Cleanup incomplete: "+str(cleanup))
        raise
def drain(meta,timeout):
    redis=Redis(meta["redis_port"])
    try:
        deadline=time.monotonic()+timeout
        while True:
            pending=redis.call("XPENDING","stream.orders","g1")
            groups=redis.call("XINFO","GROUPS","stream.orders")
            group=next((dict(zip(g[::2],g[1::2])) for g in groups if dict(zip(g[::2],g[1::2])).get("name")=="g1"),{})
            if "lag" in group:lag=group["lag"]
            else:lag=len(redis.call("XRANGE","stream.orders","("+group.get("last-delivered-id","0-0"),"+","COUNT",1))
            if pending[0]==0 and lag==0:return redis,{"pending":0,"lag":0}
            if time.monotonic()>=deadline:raise RuntimeError("Order stream not drained; pending="+str(pending[0])+", lag="+str(lag))
            time.sleep(.5)
    except Exception:redis.close();raise
def verify(args,meta):
    redis,queue=drain(meta,args.wait)
    try:
        if args.case=="reservation":
            raw=mysql(meta,"SELECT COUNT(*),COALESCE(SUM(status=2),0),COALESCE(SUM(status IN(0,1)),0) FROM tb_reservation WHERE user_id>=%d AND user_id<%d;"%(meta["start_user"],meta["start_user"]+meta["users"]),meta["database"])
            total,cancelled,active=map(int,raw.split("\t"));expected=args.expected_count if args.expected_count is not None else meta["users"]
            passed=total==expected and cancelled==expected and active==0
            report={"case":args.case,"total":total,"cancelled":cancelled,"active":active,"expected":expected,"passed":passed,**queue}
        else:
            vid=meta["vouchers"][args.case]
            count=int(mysql(meta,"SELECT COUNT(*) FROM tb_voucher_order WHERE voucher_id="+str(vid)+";",meta["database"]))
            duplicate=int(mysql(meta,"SELECT COUNT(*) FROM (SELECT user_id,COUNT(*) n FROM tb_voucher_order WHERE voucher_id="+str(vid)+" GROUP BY user_id HAVING n>1) x;",meta["database"]))
            expected=args.expected_count if args.expected_count is not None else (1 if args.case=="duplicate" else meta["users"] if args.case=="claim" else min(meta["users"],meta["stock"]))
            report={"case":args.case,"orders":count,"expected":expected,"duplicate_users":duplicate,**queue}
            passed=count==expected and duplicate==0
            if args.case!="claim":
                dbstock=int(mysql(meta,"SELECT stock FROM tb_seckill_voucher WHERE voucher_id="+str(vid)+";",meta["database"]))
                redisstock=int(redis.call("GET","seckill:stock:"+str(vid)))
                buyers=redis.call("SCARD","seckill:order:"+str(vid))
                report.update({"initial_stock":meta["stock"],"mysql_stock":dbstock,"redis_stock":redisstock,"redis_buyers":buyers})
                passed=passed and dbstock>=0 and dbstock==redisstock and count+dbstock==meta["stock"] and buyers==count
            report["passed"]=passed
        (Path(meta["dir"])/("verify-"+args.case+".json")).write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8")
        say(report)
        if not report["passed"]:raise RuntimeError("Business correctness verification failed")
    finally:redis.close()
def renew(meta):
    if meta.get("stopped"):raise RuntimeError("Test environment is stopped")
    redis=Redis(meta["redis_port"])
    try:
        with (Path(meta["dir"])/"users.csv").open(encoding="utf-8") as f:
            for row in csv.DictReader(f):redis.call("SET","login:code:"+row["phone"],row["code"],"EX",7200)
    finally:redis.close()
    say("Renewed only fixture OTP codes in the dedicated test Redis; valid for 2 hours.")
def main():
    parser=argparse.ArgumentParser(description=__doc__);sub=parser.add_subparsers(dest="command",required=True)
    p=sub.add_parser("prepare");p.add_argument("--users",type=int,default=200);p.add_argument("--stock",type=int,default=100)
    p.add_argument("--mysql");p.add_argument("--mysql-user",default="root");p.add_argument("--mysql-port",type=int,default=3306)
    p.add_argument("--redis-server",default=REDIS_DEFAULT);p.add_argument("--redis-port",type=int,default=16379);p.add_argument("--api-port",type=int,default=18081);p.add_argument("--java")
    p=sub.add_parser("verify");p.add_argument("--case",choices=["seckill","duplicate","claim","reservation"],required=True);p.add_argument("--expected-count",type=int);p.add_argument("--wait",type=int,default=30)
    p=sub.add_parser("stop");p.add_argument("--drop-test-database",action="store_true")
    sub.add_parser("renew-codes")
    args=parser.parse_args()
    if args.command=="prepare":prepare(args)
    elif args.command=="verify":verify(args,state())
    elif args.command=="renew-codes":renew(state())
    else:stop(state(),args.drop_test_database)
if __name__=="__main__":
    try:main()
    except Exception as error:say("ERROR: "+str(error));sys.exit(1)
