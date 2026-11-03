import groovy.json.JsonSlurper
// HTTP sampler starts after the synchronization timer; exclude barrier wait.
vars.put('submit_started_ms', Long.toString(prev.getStartTime()))
try {
    if (!prev.getResponseCode().startsWith('2')) throw new IllegalStateException('HTTP ' + prev.getResponseCode())
    def json = new JsonSlurper().parseText(prev.getResponseDataAsString())
    if (json.success == true && (json.data?.toString() ==~ /[1-9][0-9]*/)) {
        vars.put('order_id', json.data.toString())
        vars.put('outcome', 'ACCEPTED')
    } else if (json.success == false && json.errorMsg?.contains('库存不足')) {
        vars.put('outcome', 'SOLD_OUT')
        prev.setSuccessful(true)
    } else if (json.success == false && (json.errorMsg?.contains('每人限领') || json.errorMsg?.contains('已经领取过') || json.errorMsg?.contains('不能重复下单'))) {
        vars.put('outcome', 'DUPLICATE')
        prev.setSuccessful(true)
    } else throw new IllegalStateException(json.errorMsg ?: 'Invalid order id')
} catch (Exception error) {
    vars.put('outcome', 'SYSTEM_ERROR')
    prev.setSuccessful(false)
    prev.setResponseMessage('UNEXPECTED: ' + error.message)
}
