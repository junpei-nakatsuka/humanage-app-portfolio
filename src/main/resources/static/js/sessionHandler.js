async function fetchWithSession(url, options = {}) {
    const sessionId = localStorage.getItem('X-Auth-Token');
    if (sessionId) {
        options.headers = {
            ...options.headers,
            'X-Auth-Token': sessionId
        };
    }
    const response = await fetch(url, options);
    return response;
}
