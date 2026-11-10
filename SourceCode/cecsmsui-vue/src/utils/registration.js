export function createRegistrationPayload(form, captchaId) {
  return {
    username: form.username?.trim(),
    password: form.password,
    name: form.name?.trim(),
    telephone: form.telephone?.trim(),
    idNum: form.idNum?.trim(),
    sex: form.sex,
    address: form.address?.trim(),
    captchaId,
    verificationCode: form.verificationCode?.trim()
  }
}
