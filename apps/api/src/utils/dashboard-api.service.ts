import { Injectable, Logger } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom } from 'rxjs';
import { Request } from 'express';

/**
 * Service for forwarding authenticated requests to Dashboard API
 * Automatically extracts cookies from incoming requests and forwards them
 */
@Injectable()
export class DashboardApiService {
  private readonly logger = new Logger(DashboardApiService.name);
  private readonly dashboardApiUrl: string;

  constructor(private readonly httpService: HttpService) {
    this.dashboardApiUrl = process.env.DASHBOARD_API_URL;
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

    try {
      const { data } = await firstValueFrom(
        this.httpService.get<T>(url, {
          headers: {
            'Content-Type': 'application/json',
            Accept: 'application/json',
            ...(cookies && { Cookie: cookies }),
          },
        }),
      );

      return data;
    } catch (error) {
      this.logger.error(`Dashboard API GET ${path} failed: ${error.message}`);
      throw new Error(`Dashboard API error: ${error.response?.status || 500}`);
    }
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

    try {
      const { data } = await firstValueFrom(
        this.httpService.post<T>(url, body, {
          headers: {
            'Content-Type': 'application/json',
            Accept: 'application/json',
            ...(cookies && { Cookie: cookies }),
          },
        }),
      );

      return data;
    } catch (error) {
      this.logger.error(`Dashboard API POST ${path} failed: ${error.message}`);
      throw new Error(`Dashboard API error: ${error.response?.status || 500}`);
    }
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

    try {
      const { data } = await firstValueFrom(
        this.httpService.patch<T>(url, body, {
          headers: {
            'Content-Type': 'application/json',
            Accept: 'application/json',
            ...(cookies && { Cookie: cookies }),
          },
        }),
      );

      return data;
    } catch (error) {
      this.logger.error(`Dashboard API PATCH ${path} failed: ${error.message}`);
      throw new Error(`Dashboard API error: ${error.response?.status || 500}`);
    }
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

    try {
      await firstValueFrom(
        this.httpService.delete(url, {
          headers: {
            Accept: 'application/json',
            ...(cookies && { Cookie: cookies }),
          },
        }),
      );
    } catch (error) {
      this.logger.error(`Dashboard API DELETE ${path} failed: ${error.message}`);
      throw new Error(`Dashboard API error: ${error.response?.status || 500}`);
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
}
