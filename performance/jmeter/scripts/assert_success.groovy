import groovy.json.JsonSlurper
try {
    def result = new JsonSlurper().parseText(prev.getResponseDataAsString())
    if (!prev.getResponseCode().startsWith('2') || result.success != true) {
        AssertionResult.setFailure(true)
        AssertionResult.setFailureMessage(result.errorMsg ?: 'HTTP/business failure')
    }
} catch (Exception error) {
    AssertionResult.setFailure(true)
    AssertionResult.setFailureMessage('Response is not valid business JSON')
}
