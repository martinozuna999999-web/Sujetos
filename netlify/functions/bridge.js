const URL_GOOGLE = 'https://script.google.com/macros/s/AKfycbxiLz67kznsTCAjh-v0nsVh68qgga6T0IlXSUtEJqJY-BC1KSvWicbLvX9vML-RJCzWHQ/exec';
const allowed = new Map([['estado',0],['entregar',1],['login',1],['listar',1],['guardarAsignacion',2],['salir',1]]);
function json(statusCode, value){return {statusCode,headers:{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'},body:JSON.stringify(value)};}
exports.handler=async (event)=>{
  let payload;
  if(event.httpMethod==='GET'){payload={action:'estado',args:[]};}
  else if(event.httpMethod==='POST'){
    try {const raw=event.isBase64Encoded?Buffer.from(event.body||'','base64').toString('utf8'):event.body;if(!raw||Buffer.byteLength(raw)>300000)return json(413,{ok:false,error:'Solicitud demasiado extensa.'});payload=JSON.parse(raw);}
    catch(e){return json(400,{ok:false,error:'Solicitud inválida.'});}
  }else return json(405,{ok:false,error:'Método no permitido.'});
  if(!payload||!allowed.has(payload.action)||!Array.isArray(payload.args)||payload.args.length!==allowed.get(payload.action))return json(400,{ok:false,error:'Operación no permitida.'});
  try{
    const upstream=await fetch(URL_GOOGLE,{method:'POST',headers:{'Content-Type':'application/json; charset=utf-8','Accept':'application/json'},body:JSON.stringify({action:payload.action,args:payload.args}),redirect:'follow',signal:AbortSignal.timeout(27000)});
    if(!upstream.ok)return json(502,{ok:false,error:'Google Apps Script respondió con error HTTP '+upstream.status+'. Revisá la implementación.'});
    let data;try{data=await upstream.json()}catch(e){return json(502,{ok:false,error:'Google no devolvió JSON. Verificá que agregaste doPost, publicaste una nueva versión y permitiste acceso a cualquier persona.'});}
    if(!data||typeof data.ok!=='boolean')return json(502,{ok:false,error:'Respuesta del servidor inválida.'});
    if(data.ok===false)return json(400,{ok:false,error:typeof data.error==='string'?data.error:'Solicitud rechazada.'});
    if(!Object.hasOwn(data,'result'))return json(502,{ok:false,error:'Respuesta incompleta del servidor.'});
    return json(200,{ok:true,result:data.result});
  }catch(e){return json(502,{ok:false,error:'No se pudo conectar con Google Apps Script. Conservá el borrador y volvé a intentar.'});}
};
