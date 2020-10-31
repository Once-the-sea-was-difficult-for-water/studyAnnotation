package com.lw;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

@SpringBootTest
class DemoApplicationTests {



    private static void swap(int[] array, int i, int j) {
        int temp = array[i];
        array[i] = array[j];
        array[j] = temp;
    }

    private static void permuteRecursion(int[] nums, int start, int end, Integer[] nums_Integer, List<List<Integer>> result) {
        if (start == end) {
            /*
             * for(int i = 0; i<nums.length; i++) System.out.print(nums[i]);
             */
            /*
             *
             * Integer[] nums_Integer = new Integer[nums.length];
             */
            for (int i = 0; i < nums.length; i++)
                nums_Integer[i] = Integer.valueOf(nums[i]);

            List<Integer> temp = new ArrayList<Integer>(Arrays.asList(nums_Integer));

            result.add(temp);
        }

        for (int i = start; i <= end; i++) {
            /* 交换 start和第 i个元素 */
            swap(nums, start, i);

            permuteRecursion(nums, start + 1, end, nums_Integer, result);

            /* 交换 start和第 i个元素 */
            swap(nums, start, i);
        }
    }

    public static List<List<Integer>> permute(int[] nums) {

        List<List<Integer>> result = new ArrayList<>();

        Integer[] nums_Integer = new Integer[nums.length];

        permuteRecursion(nums, 0, nums.length - 1, nums_Integer, result);

        return result;
    }

    public static void main(String[] args) {
        // TODO Auto-generated method stub


        int[] nums = new int[] { 1, 2, 3, 4 };
        List<List<Integer>> list = permute(nums);
        Iterator<List<Integer>> itx = list.iterator();
        while (itx.hasNext()) {
            List<Integer> lt = itx.next();
            System.out.println(lt);
        }
        System.out.println();
    }

}
