import { Injectable, Logger } from '@nestjs/common';
import { Request } from 'express';

/**
 * Service for forwarding authenticated requests to Dashboard API
 * Automatically extracts cookies from incoming requests and forwards them
 */
@Injectable()
export class DashboardApiService {
  private readonly logger = new Logger(DashboardApiService.name);
  private readonly dashboardApiUrl: string;

  constructor() {
    // Dashboard API base URL from environment or default
    this.dashboardApiUrl = process.env.DASHBOARD_API_URL || 'https://api.airgradient.com';
    this.logger.log(`Dashboard API URL: ${this.dashboardApiUrl}`);
  }

  /**
   * Extract cookies from Express request
   * @param req Express request object
   * @returns Cookie header string or undefined
   */
  extractCookies(req: Request): string | undefined {
    const cookies = req.headers.cookie;

    if (cookies) {
      this.logger.debug(`Extracted cookies from request: ${cookies.substring(0, 50)}...`);
    } else {
      this.logger.debug('No cookies found in request');
    }

    return cookies;
  }

  /**
   * Forward a GET request to Dashboard API with cookies
   * @param req Express request (to extract cookies from)
   * @param path Dashboard API path (e.g., '/auth/user')
   * @returns Response from Dashboard API
   */
  async get<T>(req: Request, path: string): Promise<T> {
    const url = `${this.dashboardApiUrl}${path}`;
    const cookies = this.extractCookies(req);

    this.logger.log(`Forwarding GET ${path} to Dashboard API`);

    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...(cookies && { Cookie: cookies }),
      },
    });

    if (!response.ok) {
      const error = await response.text();
      this.logger.error(`Dashboard API GET ${path} failed: ${response.status} - ${error}`);
      throw new Error(`Dashboard API error: ${response.status}`);
    }

    return response.json() as Promise<T>;
  }

  /**
   * Forward a POST request to Dashboard API with cookies
   * @param req Express request (to extract cookies from)
   * @param path Dashboard API path
   * @param body Request body
   * @returns Response from Dashboard API
   */
  async post<T>(req: Request, path: string, body: unknown): Promise<T> {
    const url = `${this.dashboardApiUrl}${path}`;
    const cookies = this.extractCookies(req);

    this.logger.log(`Forwarding POST ${path} to Dashboard API`);

    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...(cookies && { Cookie: cookies }),
      },
      body: JSON.stringify(body),
    });

    if (!response.ok) {
      const error = await response.text();
      this.logger.error(`Dashboard API POST ${path} failed: ${response.status} - ${error}`);
      throw new Error(`Dashboard API error: ${response.status}`);
    }

    return response.json() as Promise<T>;
  }

  /**
   * Forward a PATCH request to Dashboard API with cookies
   * @param req Express request (to extract cookies from)
   * @param path Dashboard API path
   * @param body Request body
   * @returns Response from Dashboard API
   */
  async patch<T>(req: Request, path: string, body: unknown): Promise<T> {
    const url = `${this.dashboardApiUrl}${path}`;
    const cookies = this.extractCookies(req);

    this.logger.log(`Forwarding PATCH ${path} to Dashboard API`);

    const response = await fetch(url, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...(cookies && { Cookie: cookies }),
      },
      body: JSON.stringify(body),
    });

    if (!response.ok) {
      const error = await response.text();
      this.logger.error(`Dashboard API PATCH ${path} failed: ${response.status} - ${error}`);
      throw new Error(`Dashboard API error: ${response.status}`);
    }

    return response.json() as Promise<T>;
  }

  /**
   * Forward a DELETE request to Dashboard API with cookies
   * @param req Express request (to extract cookies from)
   * @param path Dashboard API path
   * @returns void
   */
  async delete(req: Request, path: string): Promise<void> {
    const url = `${this.dashboardApiUrl}${path}`;
    const cookies = this.extractCookies(req);

    this.logger.log(`Forwarding DELETE ${path} to Dashboard API`);

    const response = await fetch(url, {
      method: 'DELETE',
      headers: {
        'Accept': 'application/json',
        ...(cookies && { Cookie: cookies }),
      },
    });

    if (!response.ok) {
      const error = await response.text();
      this.logger.error(`Dashboard API DELETE ${path} failed: ${response.status} - ${error}`);
      throw new Error(`Dashboard API error: ${response.status}`);
    }
  }

  /**
   * Check if cookies exist in request
   * Useful for determining if request is authenticated
   * @param req Express request
   * @returns true if cookies exist
   */
  hasCookies(req: Request): boolean {
    return !!req.headers.cookie;
  }

  /**
   * Log cookie status (for debugging)
   * NEVER logs actual cookie values for security
   * @param req Express request
   */
  logCookieStatus(req: Request): void {
    const cookies = req.headers.cookie;
    if (cookies) {
      this.logger.log(`✅ Cookies present (length: ${cookies.length} chars)`);
    } else {
      this.logger.warn('⚠️  No cookies in request');
    }
  }
}
