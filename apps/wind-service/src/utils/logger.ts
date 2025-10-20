// Create a simple logger utility - apps/wind-service/src/utils/logger.ts
class SimpleLogger {
  private formatTimestamp(): string {
    return new Date().toISOString();
  }

  private formatMessage(level: string, component: string, message: string, meta?: any): string {
    const timestamp = this.formatTimestamp();
    const metaString = meta ? ` ${JSON.stringify(meta)}` : '';
    return `${timestamp} [${component}] ${level.toUpperCase()}: ${message}${metaString}`;
  }

  info(component: string, message: string, meta?: any) {
    console.log(this.formatMessage('info', component, message, meta));
  }

  warn(component: string, message: string, meta?: any) {
    console.warn(this.formatMessage('warn', component, message, meta));
  }

  error(component: string, message: string, meta?: any) {
    console.error(this.formatMessage('error', component, message, meta));
  }

  debug(component: string, message: string, meta?: any) {
    if (process.env.LOG_LEVEL === 'debug') {
      console.log(this.formatMessage('debug', component, message, meta));
    }
  }
}

// Export singleton instance
export const logger = new SimpleLogger();

// Create component-specific loggers
export const gfsLogger = {
  info: (message: string, meta?: any) => logger.info('gfs-downloader', message, meta),
  warn: (message: string, meta?: any) => logger.warn('gfs-downloader', message, meta),
  error: (message: string, meta?: any) => logger.error('gfs-downloader', message, meta),
  debug: (message: string, meta?: any) => logger.debug('gfs-downloader', message, meta),
};

export const converterLogger = {
  info: (message: string, meta?: any) => logger.info('grib-converter', message, meta),
  warn: (message: string, meta?: any) => logger.warn('grib-converter', message, meta),
  error: (message: string, meta?: any) => logger.error('grib-converter', message, meta),
  debug: (message: string, meta?: any) => logger.debug('grib-converter', message, meta),
};

export const s3Logger = {
  info: (message: string, meta?: any) => logger.info('s3-uploader', message, meta),
  warn: (message: string, meta?: any) => logger.warn('s3-uploader', message, meta),
  error: (message: string, meta?: any) => logger.error('s3-uploader', message, meta),
  debug: (message: string, meta?: any) => logger.debug('s3-uploader', message, meta),
};

export const schedulerLogger = {
  info: (message: string, meta?: any) => logger.info('scheduler', message, meta),
  warn: (message: string, meta?: any) => logger.warn('scheduler', message, meta),
  error: (message: string, meta?: any) => logger.error('scheduler', message, meta),
  debug: (message: string, meta?: any) => logger.debug('scheduler', message, meta),
};