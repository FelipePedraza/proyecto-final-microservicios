'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');
const { readSecret } = require('../src/config/secrets');

describe('readSecret', () => {
  let secretsDir;

  beforeEach(() => {
    secretsDir = fs.mkdtempSync(path.join(os.tmpdir(), 'notificaciones-secrets-'));
    process.env.SECRETS_DIR = secretsDir;
    delete process.env.TEST_SECRET_VALUE;
  });

  afterEach(() => {
    fs.rmSync(secretsDir, { recursive: true, force: true });
    delete process.env.SECRETS_DIR;
    delete process.env.TEST_SECRET_VALUE;
  });

  test('prefiere el archivo montado', () => {
    fs.writeFileSync(path.join(secretsDir, 'test_secret'), 'valor-seguro\n');
    process.env.TEST_SECRET_VALUE = 'fallback';

    expect(readSecret('test_secret', 'TEST_SECRET_VALUE')).toBe('valor-seguro');
  });

  test('falla explícitamente si el secreto no existe', () => {
    expect(() => readSecret('test_secret', 'TEST_SECRET_VALUE')).toThrow(
      "ERROR: Secreto 'test_secret' no configurado. "
      + 'Montar /run/secrets/test_secret o definir TEST_SECRET_VALUE.',
    );
  });

  test('usa la variable de entorno si el archivo no existe', () => {
    process.env.TEST_SECRET_VALUE = 'fallback-seguro';

    expect(readSecret('test_secret', 'TEST_SECRET_VALUE')).toBe('fallback-seguro');
  });

  test('distingue explícitamente un archivo vacío', () => {
    fs.writeFileSync(path.join(secretsDir, 'test_secret'), '  \n');

    expect(() => readSecret('test_secret', 'TEST_SECRET_VALUE')).toThrow('ERROR: Secreto \'test_secret\' está vacío.');
  });

  test.each(['REEMPLAZAR_CON_VALOR_REAL', 'GESTIONADO_EN_SECRETS_DB'])(
    'rechaza explícitamente el marcador de plantilla %p',
    (marker) => {
      fs.writeFileSync(path.join(secretsDir, 'test_secret'), marker);

      expect(() => readSecret('test_secret', 'TEST_SECRET_VALUE')).toThrow('ERROR: Secreto \'test_secret\' contiene un marcador de plantilla inseguro.');
    },
  );
});
