import groovy.json.JsonOutput
long elapsed = System.currentTimeMillis() - Long.parseLong(vars.get('submit_started_ms'))
vars.put('business_ms', Long.toString(elapsed))
boolean created = vars.get('poll_status') == 'CREATED'
vars.put('outcome', created ? 'CREATED' : (vars.get('poll_status') == 'PENDING' ? 'PENDING_TIMEOUT' : 'ORDER_FAILED'))
SampleResult.setSuccessful(created)
SampleResult.setResponseCode(created ? '200' : '500')
SampleResult.setResponseMessage(vars.get('outcome'))
SampleResult.setDataType('text')
SampleResult.setResponseData(JsonOutput.toJson([orderId:vars.get('order_id'),status:vars.get('poll_status'),businessMs:elapsed]),'UTF-8')
