import groovy.json.JsonSlurper
vars.put('token', '')
try {
    def json = new JsonSlurper().parseText(prev.getResponseDataAsString())
    def token = json.data?.toString()
    if (!prev.getResponseCode().startsWith('2') || json.success != true || token == null || !(token ==~ /[A-Za-z0-9_-]{16,128}/)) {
        throw new IllegalStateException(json.errorMsg ?: 'Missing login token')
    }
    vars.put('token', token)
} catch (Exception error) {
    prev.setSuccessful(false)
    prev.setResponseMessage('LOGIN_FAILED: ' + error.message)
    prev.setStopThread(true)
}
