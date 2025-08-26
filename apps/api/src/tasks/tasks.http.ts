import { HttpException, Injectable } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { AxiosError, AxiosRequestConfig } from 'axios';
import { firstValueFrom } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Logger } from '@nestjs/common';

@Injectable()
export class TasksHttp {
  private readonly logger = new Logger('TasksHttp');
  constructor(private readonly httpService: HttpService) {}

  private defaultHeaders: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  };

  async fetch<T>(url: string, headers?: Record<string, string>): Promise<T> {
    const requestConfig: AxiosRequestConfig = {
      headers: { ...this.defaultHeaders, ...headers },
    };

    try {
      const { data } = await firstValueFrom(
        this.httpService.get<T>(url, requestConfig).pipe(
          catchError((error: AxiosError) => {
            if (error.response) {
              // Non-200 response from server
              this.logger.warn(
                `Request failed: ${error.response.status} - ${JSON.stringify(error.response.data)}`,
              );
              throw new HttpException(error.response.data, error.response.status);
            } else {
              // No response or network error
              this.logger.error(`Request error: ${error.message}`);
              throw new Error('Network error or no response received');
            }
          }),
        ),
      );

      return data ?? null;
    } catch (error) {
      this.logger.error(`Fetch failed for ${url}: ${error.message}`);
      throw error;
    }
  }
}
