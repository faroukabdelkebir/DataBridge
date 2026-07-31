import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { API } from '../config/api.config';
import { Page } from '../models/api.model';
import {
  CreateUserRequest, UpdateUserRequest,
  UserResponse, UserSearchFilters, UserSummary,
} from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  private http = inject(HttpClient);

  search(filters: UserSearchFilters, page = 0, size = 10): Observable<Page<UserSummary>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters.search) params = params.set('search', filters.search);
    if (filters.role) params = params.set('role', filters.role);
    if (filters.active !== undefined) params = params.set('active', filters.active);
    return this.http.get<Page<UserSummary>>(API.users.base, { params });
  }

  getById(id: string): Observable<UserResponse> {
    return this.http.get<UserResponse>(API.users.byId(id));
  }

  create(payload: CreateUserRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>(API.users.base, payload);
  }

  update(id: string, payload: UpdateUserRequest): Observable<UserResponse> {
    return this.http.patch<UserResponse>(API.users.byId(id), payload);
  }

  activate(id: string): Observable<void> {
    return this.http.patch<void>(API.users.activate(id), {});
  }

  deactivate(id: string): Observable<void> {
    return this.http.patch<void>(API.users.deactivate(id), {});
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(API.users.byId(id));
  }
}