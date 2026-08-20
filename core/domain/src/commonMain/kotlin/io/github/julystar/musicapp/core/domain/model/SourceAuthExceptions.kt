package io.github.julystar.musicapp.core.domain.model

class NeedsReauthenticationException : IllegalStateException("remote account requires reauthentication")

class OpenListOtpRequiredException : IllegalStateException("OpenList account requires an OTP")
