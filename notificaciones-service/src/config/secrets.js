'use strict';

const fs = require('fs');
const path = require('path');

function readSecret(secretName, envFallback) {
  const secretsDir = process.env.SECRETS_DIR || '/run/secrets';
  const secretPath = path.join(secretsDir, secretName);
  let value = '';

  try {
    value = fs.readFileSync(secretPath, 'utf8').trim();
  } catch (error) {
    if (error.code !== 'ENOENT') {
      throw new Error(`ERROR: No se pudo leer el secreto '${secretName}' desde ${secretPath}.`, { cause: error });
    }
    value = process.env[envFallback]?.trim();
  }

  const hint = ` Montar /run/secrets/${secretName} o definir ${envFallback}.`;
  if (value === undefined) {
    throw new Error(
      `ERROR: Secreto '${secretName}' no configurado.${hint}`,
    );
  }
  if (!value) {
    throw new Error(`ERROR: Secreto '${secretName}' está vacío.${hint}`);
  }
  if (value.startsWith('REEMPLAZAR') || value.startsWith('GESTIONADO_EN_SECRETS')) {
    throw new Error(
      `ERROR: Secreto '${secretName}' contiene un marcador de plantilla inseguro.${hint}`,
    );
  }
  return value;
}

module.exports = { readSecret };
