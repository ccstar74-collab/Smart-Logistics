package com.smartlogistics.agent;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Authenticated vehicle-location WebSocket client with heartbeat and reconnect. */
final class RealtimeVehicleService implements WebSocket.Listener {
    interface TokenProvider { String token(boolean refresh) throws Exception; }

    private final String endpoint;
    private final TokenProvider tokenProvider;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ScheduledExecutorService scheduler=Executors.newScheduledThreadPool(2,r->{Thread t=new Thread(r,"realtime-vehicle-ws");t.setDaemon(true);return t;});
    private final Map<String,Map<String,Object>> latest=new java.util.concurrent.ConcurrentHashMap<>();
    private final AtomicBoolean reconnectScheduled=new AtomicBoolean();
    private final StringBuilder messageBuffer=new StringBuilder();
    private volatile WebSocket socket;private volatile ScheduledFuture<?> heartbeat;private volatile int retryCount;private volatile long lastPongAt;

    RealtimeVehicleService(String url,TokenProvider tokenProvider){this(url,tokenProvider,true);}
    RealtimeVehicleService(String url,TokenProvider tokenProvider,boolean autoConnect){this.endpoint=url;this.tokenProvider=tokenProvider;if(autoConnect)connect(false);}

    private void connect(boolean refreshToken){
        try{
            String token=tokenProvider.token(refreshToken);if(token==null||token.isBlank())throw new IllegalStateException("未配置 WebSocket JWT");
            URI uri=authenticatedUri(endpoint,token);
            client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(8)).buildAsync(uri,this)
                    .whenComplete((ws,error)->{if(error!=null){System.err.println("Realtime WebSocket connect failed: "+safe(error));scheduleReconnect(true);}});
        }catch(Exception e){System.err.println("Realtime WebSocket token/connect failed: "+safe(e));scheduleReconnect(true);}
    }

    Map<String,Object> get(String id){if(id==null)return null;String key=id.trim();Map<String,Object> value=latest.get(key);if(value==null)value=latest.get(key.toLowerCase(Locale.ROOT));return value;}
    boolean acceptForTest(String message){return accept(message);}

    @Override public void onOpen(WebSocket ws){socket=ws;retryCount=0;reconnectScheduled.set(false);lastPongAt=System.currentTimeMillis();startHeartbeat(ws);System.out.println("Realtime WebSocket connected: "+endpoint);ws.request(1);}
    @Override public synchronized CompletionStage<?> onText(WebSocket ws,CharSequence data,boolean last){messageBuffer.append(data);if(last){String message=messageBuffer.toString();messageBuffer.setLength(0);if("pong".equals(message.trim()))lastPongAt=System.currentTimeMillis();else accept(message);}ws.request(1);return null;}
    @Override public CompletionStage<?> onBinary(WebSocket ws,ByteBuffer data,boolean last){byte[] bytes=new byte[data.remaining()];data.get(bytes);return onText(ws,new String(bytes,StandardCharsets.UTF_8),last);}
    @Override public CompletionStage<?> onClose(WebSocket ws,int status,String reason){stopHeartbeat();socket=null;System.err.println("Realtime WebSocket closed: "+status+" "+reason);scheduleReconnect(status==1008||status==1011);return null;}
    @Override public void onError(WebSocket ws,Throwable error){stopHeartbeat();socket=null;System.err.println("Realtime WebSocket error: "+safe(error));scheduleReconnect(true);}

    private boolean accept(String message){
        try{
            String trimmed=message==null?"":message.trim();if(trimmed.isEmpty()||"ping".equals(trimmed)||"pong".equals(trimmed))return false;
            int objectBegin=trimmed.indexOf('{'),arrayBegin=trimmed.indexOf('[');int begin=objectBegin<0?arrayBegin:(arrayBegin<0?objectBegin:Math.min(objectBegin,arrayBegin));if(begin<0)return false;
            Object parsed=Json.parse(trimmed.substring(begin));return acceptValue(parsed);
        }catch(Exception e){System.err.println("Realtime WebSocket message parse failed: "+e.getMessage());return false;}
    }

    @SuppressWarnings("unchecked")
    private boolean acceptValue(Object value){
        if(value instanceof Iterable<?> values){boolean accepted=false;for(Object item:values)accepted=acceptValue(item)||accepted;return accepted;}
        if(!(value instanceof Map<?,?> raw))return false;
        Map<String,Object> obj=new LinkedHashMap<>((Map<String,Object>)raw);
        Object data=obj.get("data");if(data instanceof Iterable<?>)return acceptValue(data);
        if(data instanceof Map<?,?> nested)obj.putAll((Map<String,Object>)nested);
        Object gps=obj.get("gps");if(gps instanceof Iterable<?>)return acceptValue(gps);
        if(gps instanceof Map<?,?> nestedGps)obj.putAll((Map<String,Object>)nestedGps);
        double longitude=firstNumber(obj,"longitude","lon","lng"),latitude=firstNumber(obj,"latitude","lat");
        if(!Double.isFinite(longitude)||longitude< -180||longitude>180||!Double.isFinite(latitude)||latitude< -90||latitude>90||(longitude==0&&latitude==0))return false;
        String vehicleId=identifier(firstValue(obj,"vehicleId","vehicle_id"));
        String sim=firstText(obj,"simCode","sim_code","deviceId","device_id").toLowerCase(Locale.ROOT);
        if(sim.isEmpty()&&vehicleId.toLowerCase(Locale.ROOT).startsWith("sim_"))sim=vehicleId.toLowerCase(Locale.ROOT);
        if(sim.isEmpty()&&vehicleId.isEmpty())return false;
        Map<String,Object> normalized=new LinkedHashMap<>(obj);normalized.put("longitude",longitude);normalized.put("latitude",latitude);
        normalized.put("vehicleId",vehicleId);if(!sim.isEmpty())normalized.put("simCode",sim);
        normalized.put("collectedAt",firstValue(obj,"collectedAt","timestamp","collectTime"));
        String coordinateSystem=firstText(obj,"coordinateSystem","coordSystem");normalized.put("coordSystem",coordinateSystem.isEmpty()?"WGS84":coordinateSystem.toUpperCase(Locale.ROOT));
        if(!sim.isEmpty())latest.put(sim,normalized);if(!vehicleId.isEmpty())latest.put(vehicleId,normalized);return true;
    }

    private void startHeartbeat(WebSocket ws){stopHeartbeat();heartbeat=scheduler.scheduleAtFixedRate(()->{if(socket!=ws)return;long now=System.currentTimeMillis();if(now-lastPongAt>75000){try{ws.sendClose(1000,"heartbeat timeout");}catch(Exception ignored){}scheduleReconnect(false);return;}try{ws.sendText("ping",true);}catch(Exception e){scheduleReconnect(false);}},25,25,TimeUnit.SECONDS);}
    private void stopHeartbeat(){ScheduledFuture<?> task=heartbeat;if(task!=null)task.cancel(false);heartbeat=null;}
    private void scheduleReconnect(boolean refreshToken){if(!reconnectScheduled.compareAndSet(false,true))return;stopHeartbeat();long delay=Math.min(30,1L<<Math.min(retryCount++,5));scheduler.schedule(()->{reconnectScheduled.set(false);connect(refreshToken);},delay,TimeUnit.SECONDS);}

    private static URI authenticatedUri(String endpoint,String token){String separator=endpoint.contains("?")?"&":"?";return URI.create(endpoint+separator+"token="+URLEncoder.encode(token,StandardCharsets.UTF_8));}
    private static String safe(Throwable error){String value=error==null?"unknown":String.valueOf(error.getMessage());return value.replaceAll("(?i)(token=)[^&\\s]+","$1***");}
    private static String text(Object value){return value==null?"":String.valueOf(value).trim();}
    private static String identifier(Object value){if(value instanceof Number n){double d=n.doubleValue();if(Double.isFinite(d)&&d==Math.rint(d))return String.valueOf((long)d);}return text(value);}
    private static double number(Object value){try{return value instanceof Number n?n.doubleValue():Double.parseDouble(String.valueOf(value));}catch(Exception e){return Double.NaN;}}
    private static Object firstValue(Map<String,Object> value,String...keys){for(String key:keys)if(value.get(key)!=null)return value.get(key);return null;}
    private static String firstText(Map<String,Object> value,String...keys){return text(firstValue(value,keys));}
    private static double firstNumber(Map<String,Object> value,String...keys){return number(firstValue(value,keys));}
}
