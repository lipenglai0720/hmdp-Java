import groovy.json.JsonSlurper
vars.put('poll_count', Integer.toString(Integer.parseInt(vars.get('poll_count')) + 1))
try {
    def json = new JsonSlurper().parseText(prev.getResponseDataAsString())
    if (!prev.getResponseCode().startsWith('2') || json.success != true) throw new IllegalStateException(json.errorMsg ?: 'HTTP failure')
    def status = json.data?.status?.toString()
    if (!(status in ['PENDING','CREATED','FAILED'])) throw new IllegalStateException('Unknown order state')
    vars.put('poll_status', status)
    if (status == 'FAILED') {
        prev.setSuccessful(false)
        prev.setResponseMessage(json.data.message ?: 'Order creation failed')
    }
} catch (Exception error) {
    vars.put('poll_status', 'ERROR')
    prev.setSuccessful(false)
    prev.setResponseMessage('POLL_FAILED: ' + error.message)
}
