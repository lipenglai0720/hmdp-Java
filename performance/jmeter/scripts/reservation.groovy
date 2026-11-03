import groovy.json.JsonSlurper
vars.put('reservation_id', '')
try {
    def json = new JsonSlurper().parseText(prev.getResponseDataAsString())
    if (!prev.getResponseCode().startsWith('2') || json.success != true || !(json.data?.toString() ==~ /[1-9][0-9]*/)) throw new IllegalStateException(json.errorMsg ?: 'Invalid reservation result')
    vars.put('reservation_id', json.data.toString())
} catch (Exception error) {
    prev.setSuccessful(false)
    prev.setResponseMessage('RESERVATION_FAILED: ' + error.message)
}
