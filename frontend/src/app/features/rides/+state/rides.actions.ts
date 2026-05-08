import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { RideDetail, RidePage } from '../../../core/api';

export const RidesActions = createActionGroup({
  source: 'Rides',
  events: {
    'Load Rides': props<{ page: number; size: number }>(),
    'Load Rides Success': props<{ ridePage: RidePage }>(),
    'Load Rides Failure': props<{ error: string }>(),

    'Load Ride': props<{ id: number }>(),
    'Load Ride Success': props<{ ride: RideDetail }>(),
    'Load Ride Failure': props<{ error: string }>(),
  },
});
